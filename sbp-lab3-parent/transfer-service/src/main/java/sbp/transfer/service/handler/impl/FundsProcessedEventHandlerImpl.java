package sbp.transfer.service.handler.impl; // Реализации хендлеров

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

  private static final String DEFAULT_RECIPIENT_EMAIL_FOR_CHEQUE =
    "cheque_recipient@example.com";

  @Override
  @Transactional(Transactional.TxType.REQUIRED)
  public void handleEvent(FundsProcessedEvent event) {
    log.info(
      "[{}] Handling FundsProcessedEvent. Success: {}, Reason: {}",
      event.getCorrelationId(),
      event.isSuccess(),
      event.getReason()
    );

    Transfer transfer = transferRepository
      .findByCorrelationId(event.getCorrelationId())
      .orElseThrow(() -> {
        log.error(
          "[{}] CRITICAL: Transfer not found for FundsProcessedEvent.",
          event.getCorrelationId()
        );
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

    switch (transfer.getStatus()) {
      case PROCESSING_RESERVATION:
        processReservationResponse(transfer, event);
        break;
      case PROCESSING_FUNDS:
        processFinalDebitResponse(transfer, event);
        break;
      default:
        log.warn(
          "[{}] Received FundsProcessedEvent for transfer (DB ID: {}) with unexpected status: {}. Ignoring event.",
          transfer.getCorrelationId(),
          transfer.getId(),
          transfer.getStatus()
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

  private void processReservationResponse(
    Transfer transfer,
    FundsProcessedEvent event
  ) {
    if (event.isSuccess()) {
      transfer.setStatus(TransferStatus.AWAITING_CONFIRMATION);
      log.info(
        "[{}] Funds successfully reserved. Transfer status -> AWAITING_CONFIRMATION.",
        transfer.getCorrelationId()
      );
    } else {
      transfer.setStatus(TransferStatus.RESERVATION_FAILED);
      transfer.setFailureReason(
        event.getReason() != null
          ? event.getReason()
          : "Funds reservation failed at AccountService."
      );
      log.warn(
        "[{}] Funds reservation failed. Reason: {}. Transfer status -> RESERVATION_FAILED.",
        transfer.getCorrelationId(),
        transfer.getFailureReason()
      );
      sendFailureNotification(transfer);
    }
  }

  private void processFinalDebitResponse(
    Transfer transfer,
    FundsProcessedEvent event
  ) {
    if (event.isSuccess()) {
      transfer.setStatus(TransferStatus.PROCESSING_EIS);
      log.info(
        "[{}] Final funds debit/credit successful. Transfer status -> PROCESSING_EIS.",
        transfer.getCorrelationId()
      );
      try {
        log.info(
          "[{}] Attempting to send electronic cheque via EIS for transfer (DB ID: {}).",
          transfer.getCorrelationId(),
          transfer.getId()
        );
        ChequeDetailsDTO chequeDetails = ChequeDetailsDTO.builder()
          .transactionId(transfer.getId())
          .recipientEmail(DEFAULT_RECIPIENT_EMAIL_FOR_CHEQUE)
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
        eisService.sendElectronicCheque(chequeDetails);
        transfer.setStatus(TransferStatus.SUCCESSFUL);
        log.info(
          "[{}] EIS processing successful. Transfer status -> SUCCESSFUL.",
          transfer.getCorrelationId()
        );
        sendSuccessNotification(transfer);
      } catch (Exception e) {
        log.error(
          "[{}] Error sending electronic cheque via EIS for transfer (DB ID: {}): {}. Transfer status -> EIS_ERROR.",
          transfer.getCorrelationId(),
          transfer.getId(),
          e.getMessage(),
          e
        );
        transfer.setStatus(TransferStatus.EIS_ERROR);
        transfer.setFailureReason("EIS error: " + e.getMessage());
        sendFailureNotification(transfer);
      }
    } else {
      transfer.setStatus(TransferStatus.FUNDS_TRANSFER_FAILED);
      transfer.setFailureReason(
        event.getReason() != null
          ? event.getReason()
          : "Final funds debit/credit failed at AccountService."
      );
      log.warn(
        "[{}] Final funds debit/credit failed. Reason: {}. Transfer status -> FUNDS_TRANSFER_FAILED.",
        transfer.getCorrelationId(),
        transfer.getFailureReason()
      );
      sendFailureNotification(transfer);
    }
  }

  private void sendSuccessNotification(Transfer transfer) {
    if (transfer == null) return;
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
        "[{}] Sent SendSuccessNotificationCommand.",
        transfer.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendSuccessNotificationCommand: {}",
        transfer.getCorrelationId(),
        e.getMessage(),
        e
      );
    }
  }

  private void sendFailureNotification(Transfer transfer) {
    if (transfer == null) return;
    SendFailureNotificationCommand cmd =
      SendFailureNotificationCommand.builder()
        .correlationId(transfer.getCorrelationId())
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .amount(transfer.getAmount())
        .reason(transfer.getFailureReason())
        .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_FAILURE_CMD_QUEUE,
        cmd
      );
      log.info(
        "[{}] Sent SendFailureNotificationCommand. Reason: {}",
        transfer.getCorrelationId(),
        transfer.getFailureReason()
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendFailureNotificationCommand: {}",
        transfer.getCorrelationId(),
        e.getMessage(),
        e
      );
    }
  }
}
