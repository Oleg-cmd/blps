package sbp.transfer.service.handler.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import sbp.dto.EisService;
import sbp.dto.JmsQueueNames;
import sbp.dto.eis.ChequeDetailsDTO;
import sbp.dto.enums.TransferStatus;
import sbp.dto.jms.FundsProcessedEvent;
import sbp.dto.jms.SendFailureNotificationCommand;
import sbp.dto.jms.SendSuccessNotificationCommand;
import sbp.transfer.entity.Transfer;
import sbp.transfer.repository.TransferRepository;
import sbp.transfer.service.handler.FundsProcessedEventHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundsProcessedEventHandlerImpl
  implements FundsProcessedEventHandler {

  private final TransferRepository transferRepository;
  private final JmsTemplate jmsTemplate;
  private final EisService eisService;

  @Override
  @Transactional(Transactional.TxType.REQUIRED)
  public void handleEvent(FundsProcessedEvent event) {
    log.info(
      "[{}] Handling FundsProcessedEvent. Success: {}, RecipientEmail: {}, Reason: {}",
      event.getCorrelationId(),
      event.isSuccess(),
      event.getRecipientEmail(),
      event.getReason()
    );

    Transfer transfer = transferRepository
      .findByCorrelationId(event.getCorrelationId())
      .orElseThrow(() -> {
        log.error(
          "[{}] CRITICAL: Transfer not found for FundsProcessedEvent.",
          event.getCorrelationId()
        );
        // Если трансфер не найден, дальнейшая обработка невозможна.
        // JTA транзакция откатится (если была начата слушателем).
        return new IllegalStateException(
          "Transfer not found by correlationId: " +
          event.getCorrelationId() +
          " while processing FundsProcessedEvent."
        );
      });

    log.debug(
      "[{}] Found transfer (DB ID: {}) with current status: {} for FundsProcessedEvent.",
      transfer.getCorrelationId(),
      transfer.getId(),
      transfer.getStatus()
    );

    // Проверяем, что событие не устарело (трансфер уже в финальном статусе)
    if (isTransferInFinalState(transfer.getStatus())) {
      log.warn(
        "[{}] Transfer (DB ID: {}) is already in a final state ({}). Ignoring FundsProcessedEvent.",
        transfer.getCorrelationId(),
        transfer.getId(),
        transfer.getStatus()
      );
      return;
    }

    switch (transfer.getStatus()) {
      case PROCESSING_RESERVATION:
        processReservationResponse(transfer, event);
        break;
      case PROCESSING_FUNDS:
        processFinalDebitResponse(transfer, event);
        break;
      default:
        log.warn(
          "[{}] Received FundsProcessedEvent for transfer (DB ID: {}) with unexpected status: {}. Current logic might not handle this. Event success: {}, reason: {}",
          transfer.getCorrelationId(),
          transfer.getId(),
          transfer.getStatus(),
          event.isSuccess(),
          event.getReason()
        );

        return;
    }
    transferRepository.save(transfer);
    log.info(
      "[{}] Transfer (DB ID: {}) status updated to {} and saved.",
      transfer.getCorrelationId(),
      transfer.getId(),
      transfer.getStatus()
    );
  }

  private boolean isTransferInFinalState(TransferStatus status) {
    return switch (status) {
      case SUCCESSFUL,
        FAILED,
        RESERVATION_FAILED,
        CONFIRMATION_FAILED,
        SBP_ERROR,
        FUNDS_TRANSFER_FAILED,
        EIS_ERROR,
        LIMIT_EXCEEDED,
        INVALID_RECIPIENT,
        TIMEOUT,
        CANCELLED -> true;
      default -> false;
    };
  }

  private void processReservationResponse(
    Transfer transfer,
    FundsProcessedEvent event
  ) {
    if (event.isSuccess()) {
      transfer.setStatus(TransferStatus.AWAITING_CONFIRMATION);
      log.info(
        "[{}] Funds successfully reserved. Transfer (DB ID: {}) status -> AWAITING_CONFIRMATION.",
        transfer.getCorrelationId(),
        transfer.getId()
      );
    } else {
      transfer.setStatus(TransferStatus.RESERVATION_FAILED);
      transfer.setFailureReason(
        event.getReason() != null
          ? event.getReason()
          : "Funds reservation failed at AccountService."
      );
      log.warn(
        "[{}] Funds reservation failed for Transfer (DB ID: {}). Reason: {}. Status -> RESERVATION_FAILED.",
        transfer.getCorrelationId(),
        transfer.getId(),
        transfer.getFailureReason()
      );
      sendFailureNotification(transfer); // Уведомляем пользователя о неудаче резервирования
    }
  }

  private void processFinalDebitResponse(
    Transfer transfer,
    FundsProcessedEvent event
  ) {
    if (event.isSuccess()) {
      transfer.setStatus(TransferStatus.PROCESSING_EIS);
      log.info(
        "[{}] Final funds debit/credit successful. Transfer (DB ID: {}) status -> PROCESSING_EIS.",
        transfer.getCorrelationId(),
        transfer.getId()
      );

      String recipientEmail = event.getRecipientEmail();

      if (recipientEmail == null || recipientEmail.isBlank()) {
        log.warn(
          "[{}] Recipient email is not available in FundsProcessedEvent for transfer (DB ID: {}). Electronic cheque will not be sent.",
          transfer.getCorrelationId(),
          transfer.getId()
        );
        // Чек не отправляем, но перевод считаем успешным на этом этапе
        transfer.setStatus(TransferStatus.SUCCESSFUL);
        log.info(
          "[{}] Transfer (DB ID: {}) marked SUCCESSFUL without sending cheque (recipient email missing).",
          transfer.getCorrelationId(),
          transfer.getId()
        );
        sendSuccessNotification(transfer);
        return;
      }

      try {
        log.info(
          "[{}] Attempting to send electronic cheque via EIS for transfer (DB ID: {}) to email: {}.",
          transfer.getCorrelationId(),
          transfer.getId(),
          recipientEmail
        );
        ChequeDetailsDTO chequeDetails = ChequeDetailsDTO.builder()
          .transactionId(transfer.getId())
          .recipientEmail(recipientEmail)
          .senderInfo("SBP Transfer from: " + transfer.getSenderPhoneNumber())
          .recipientInfo(
            "To: " +
            transfer.getRecipientPhoneNumber() +
            " (Bank: " +
            transfer.getRecipientBankId() +
            ")"
          )
          .amount(transfer.getAmount())
          .transactionTimestamp(
            transfer.getUpdatedAt() != null
              ? transfer.getUpdatedAt()
              : transfer.getCreatedAt()
          )
          .subject(
            "Electronic Cheque for SBP Transfer #" +
            transfer.getId().toString().substring(0, 8).toUpperCase()
          )
          .operationDetails("SBP Transfer Payment")
          .build();

        eisService.sendElectronicCheque(chequeDetails); // Вызываем JCA коннектор

        transfer.setStatus(TransferStatus.SUCCESSFUL);
        log.info(
          "[{}] EIS processing (cheque sending) successful for transfer (DB ID: {}). Status -> SUCCESSFUL.",
          transfer.getCorrelationId(),
          transfer.getId()
        );
        sendSuccessNotification(transfer); // Уведомляем об успешном завершении всего процесса
      } catch (Exception e) {
        // Эта ошибка от JCA-коннектора
        log.error(
          "[{}] Error sending electronic cheque via EIS for transfer (DB ID: {}): {}. Status -> EIS_ERROR.",
          transfer.getCorrelationId(),
          transfer.getId(),
          e.getMessage(),
          e // Полный стектрейс ошибки
        );
        transfer.setStatus(TransferStatus.EIS_ERROR);
        transfer.setFailureReason("EIS error: " + e.getMessage());
        // Важно: Основной перевод средств УЖЕ прошел успешно в AccountService.
        // Ошибка EIS не должна откатывать перевод денег.
        // Поэтому мы все равно отправляем пользователю уведомление об УСПЕШНОМ ПЕРЕВОДЕ.
        // А проблема с чеком - это внутренняя проблема, которую можно отдельно мониторить.
        sendSuccessNotification(transfer);
      }
    } else { // FundsProcessedEvent.success == false на этапе финального списания
      transfer.setStatus(TransferStatus.FUNDS_TRANSFER_FAILED);
      transfer.setFailureReason(
        event.getReason() != null
          ? event.getReason()
          : "Final funds debit/credit failed at AccountService."
      );
      log.warn(
        "[{}] Final funds debit/credit failed for Transfer (DB ID: {}). Reason: {}. Status -> FUNDS_TRANSFER_FAILED.",
        transfer.getCorrelationId(),
        transfer.getId(),
        transfer.getFailureReason()
      );
      sendFailureNotification(transfer);
    }
  }

  private void sendSuccessNotification(Transfer transfer) {
    if (transfer == null) {
      log.warn("Attempted to send success notification for a null transfer.");
      return;
    }
    SendSuccessNotificationCommand cmd =
      SendSuccessNotificationCommand.builder()
        .correlationId(transfer.getCorrelationId())
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .amount(transfer.getAmount())
        .recipientInfo(
          transfer.getRecipientPhoneNumber() +
          " (Bank: " +
          transfer.getRecipientBankId() +
          ")"
        )
        .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_SUCCESS_CMD_QUEUE,
        cmd
      );
      log.info(
        "[{}] Sent SendSuccessNotificationCommand for transfer (DB ID: {}).",
        transfer.getCorrelationId(),
        transfer.getId()
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendSuccessNotificationCommand for transfer (DB ID: {}): {}",
        transfer.getCorrelationId(),
        transfer.getId(),
        e.getMessage(),
        e
      );
      // Ошибка отправки уведомления не должна откатывать JTA транзакцию, если основная операция завершена.
    }
  }

  private void sendFailureNotification(Transfer transfer) {
    if (transfer == null) {
      log.warn("Attempted to send failure notification for a null transfer.");
      return;
    }
    SendFailureNotificationCommand cmd =
      SendFailureNotificationCommand.builder()
        .correlationId(transfer.getCorrelationId())
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .amount(transfer.getAmount())
        .reason(
          transfer.getFailureReason() != null
            ? transfer.getFailureReason()
            : "Unknown error"
        )
        .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_FAILURE_CMD_QUEUE,
        cmd
      );
      log.info(
        "[{}] Sent SendFailureNotificationCommand for transfer (DB ID: {}). Reason: {}",
        transfer.getCorrelationId(),
        transfer.getId(),
        cmd.getReason()
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendFailureNotificationCommand for transfer (DB ID: {}): {}",
        transfer.getCorrelationId(),
        transfer.getId(),
        e.getMessage(),
        e
      );
    }
  }
}
