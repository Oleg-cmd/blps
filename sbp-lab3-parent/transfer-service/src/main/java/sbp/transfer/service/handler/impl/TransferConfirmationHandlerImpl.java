package sbp.transfer.service.handler.impl;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import sbp.dto.JmsQueueNames;
import sbp.dto.enums.TransferStatus;
import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.SendFailureNotificationCommand;
import sbp.dto.rest.TransferConfirmationResponse;
import sbp.transfer.entity.Transfer;
import sbp.transfer.repository.TransferRepository;
import sbp.transfer.service.handler.TransferConfirmationHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferConfirmationHandlerImpl
  implements TransferConfirmationHandler {

  private final TransferRepository transferRepository;
  private final JmsTemplate jmsTemplate;

  @Value("${transfer.confirmation.max-attempts:3}")
  private int maxConfirmationAttempts;

  @Override
  @Transactional(Transactional.TxType.REQUIRED)
  public TransferConfirmationResponse confirmTransfer(
    UUID transferId,
    String userProvidedCode,
    String authenticatedUser
  ) {
    log.info(
      "Attempting to confirm transferId: {} by user {} with code (masked): {}",
      transferId,
      authenticatedUser,
      maskCode(userProvidedCode)
    );

    Transfer transfer = transferRepository
      .findById(transferId)
      .orElseThrow(() -> {
        log.warn(
          "Transfer not found for confirmation. TransferId: {}",
          transferId
        );
        return new IllegalArgumentException(
          "Transfer with ID " + transferId + " not found."
        );
      });

    log.debug(
      "Found transfer: ID={}, correlationId={}, status={}, sender={}, attempts={}",
      transfer.getId(),
      transfer.getCorrelationId(),
      transfer.getStatus(),
      transfer.getSenderPhoneNumber(),
      transfer.getConfirmationAttempts()
    );

    if (!transfer.getSenderPhoneNumber().equals(authenticatedUser)) {
      log.warn(
        "Security alert: User {} trying to confirm transfer {} belonging to {}.",
        authenticatedUser,
        transferId,
        transfer.getSenderPhoneNumber()
      );
      throw new SecurityException(
        "You are not authorized to confirm this transfer."
      );
    }

    if (transfer.getStatus() != TransferStatus.AWAITING_CONFIRMATION) {
      log.warn(
        "Transfer {} is not in AWAITING_CONFIRMATION state. Current state: {}",
        transferId,
        transfer.getStatus()
      );
      // Если уже финальный статус, просто возвращаем его
      if (
        transfer.getStatus() == TransferStatus.SUCCESSFUL ||
        transfer.getStatus() == TransferStatus.FAILED ||
        transfer.getStatus() == TransferStatus.CONFIRMATION_FAILED || // Добавлено
        transfer.getStatus() == TransferStatus.PROCESSING_FUNDS ||
        transfer.getStatus() == TransferStatus.PROCESSING_EIS
      ) {
        return new TransferConfirmationResponse(
          transferId,
          transfer.getStatus(),
          "Transfer already processed or in final state: " +
          transfer.getStatus() +
          (transfer.getFailureReason() != null
              ? " (" + transfer.getFailureReason() + ")"
              : "")
        );
      }
      // Для других не-AWAITING_CONFIRMATION статусов
      return new TransferConfirmationResponse(
        transferId,
        transfer.getStatus(),
        "Transfer is not awaiting confirmation. Current status: " +
        transfer.getStatus()
      );
    }

    if (transfer.getSbpConfirmationCode() == null) {
      log.error(
        "CRITICAL: sbpConfirmationCode is null for transferId {} in AWAITING_CONFIRMATION state.",
        transferId
      );
      transfer.setStatus(TransferStatus.FAILED);
      transfer.setFailureReason(
        "Internal error: Missing system confirmation code."
      );
      transfer.setUserProvidedCode(userProvidedCode);
      transferRepository.save(transfer);
      sendFailureNotification(
        transfer,
        "Internal processing error. Please try initiating a new transfer."
      );
      return new TransferConfirmationResponse(
        transferId,
        transfer.getStatus(),
        "Internal processing error, please contact support."
      );
    }

    // Сохраняем введенный пользователем код для истории
    transfer.setUserProvidedCode(userProvidedCode);

    if (!transfer.getSbpConfirmationCode().equals(userProvidedCode)) {
      log.warn(
        "Confirmation code mismatch for transferId {}. Expected (masked): {}, Provided (masked): {}",
        transferId,
        maskCode(transfer.getSbpConfirmationCode()),
        maskCode(userProvidedCode)
      );

      transfer.setConfirmationAttempts(transfer.getConfirmationAttempts() + 1);

      String responseMessage;
      if (transfer.getConfirmationAttempts() >= maxConfirmationAttempts) {
        log.warn(
          "Max confirmation attempts reached for transferId {}",
          transferId
        );
        transfer.setStatus(TransferStatus.CONFIRMATION_FAILED);
        responseMessage = "Invalid confirmation code. Max attempts reached.";
        transfer.setFailureReason(responseMessage);
        sendFailureNotification(transfer, responseMessage);
        // Согласно "Уточненному потоку", при CONFIRMATION_FAILED мы не отправляем ReleaseFunds(isFinalDebit=false)
        // Если это нужно, здесь было бы место для этого.
      } else {
        // Статус остается AWAITING_CONFIRMATION
        responseMessage =
          "Invalid confirmation code. Attempts left: " +
          (maxConfirmationAttempts - transfer.getConfirmationAttempts());
        transfer.setFailureReason(responseMessage);
        // По "Уточненному потоку" уведомление об ошибке отправляется при финальном CONFIRMATION_FAILED.
        // Если нужно уведомлять о каждой попытке, раскомментировать:
        // sendFailureNotification(transfer, responseMessage);
      }
      transferRepository.save(transfer);
      return new TransferConfirmationResponse(
        transferId,
        transfer.getStatus(),
        responseMessage
      );
    }

    // Код верный
    log.info("Confirmation code matched for transferId: {}.", transferId);
    transfer.setStatus(TransferStatus.PROCESSING_FUNDS);
    // transfer.setConfirmationAttempts(0); // Можно сбросить, или не трогать, если счетчик только для неудачных
    transfer.setFailureReason(null); // Очищаем причину предыдущих неудач, если были
    transferRepository.save(transfer);

    ReleaseFundsCommand releaseFundsCmd = ReleaseFundsCommand.builder()
      .correlationId(transfer.getCorrelationId())
      .senderPhoneNumber(transfer.getSenderPhoneNumber())
      .recipientPhoneNumber(transfer.getRecipientPhoneNumber())
      .amount(transfer.getAmount())
      .isFinalDebit(true)
      .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.ACCOUNT_RELEASE_FUNDS_CMD_QUEUE,
        releaseFundsCmd
      );
      log.info(
        "Sent ReleaseFundsCommand to AccountService for transferId: {}, correlationId: {}. Queue: {}",
        transfer.getId(),
        transfer.getCorrelationId(),
        JmsQueueNames.ACCOUNT_RELEASE_FUNDS_CMD_QUEUE
      );
    } catch (Exception e) {
      log.error(
        "Failed to send ReleaseFundsCommand for transferId {}: {}",
        transferId,
        e.getMessage(),
        e
      );
      // Важно: если отправка JMS не удалась, JTA транзакция должна откатиться,
      // включая изменение статуса на PROCESSING_FUNDS.
      throw new RuntimeException(
        "Failed to send ReleaseFundsCommand to JMS, rolling back transfer confirmation",
        e
      );
    }

    return new TransferConfirmationResponse(
      transferId,
      transfer.getStatus(),
      "Transfer confirmation accepted, processing funds."
    );
  }

  private String maskCode(String code) {
    if (code == null) return "NULL";
    if (code.length() <= 2) return "****";
    return "****" + code.substring(Math.max(0, code.length() - 2));
  }

  private void sendFailureNotification(Transfer transfer, String reason) {
    if (transfer == null) return;
    // Используем correlationId, так как это сквозной идентификатор
    sendFailureNotification(
      transfer.getCorrelationId(),
      transfer.getSenderPhoneNumber(),
      transfer.getAmount(),
      reason
    );
  }

  private void sendFailureNotification(
    UUID correlationId,
    String senderPhoneNumber,
    BigDecimal amount,
    String reason
  ) {
    SendFailureNotificationCommand notificationCmd =
      SendFailureNotificationCommand.builder()
        .correlationId(correlationId)
        .senderPhoneNumber(senderPhoneNumber)
        .amount(amount)
        .reason(reason)
        .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_FAILURE_CMD_QUEUE,
        notificationCmd
      );
      log.info(
        "[{}] Sent SendFailureNotificationCommand. Reason: {}",
        correlationId,
        reason
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendFailureNotificationCommand: {}",
        correlationId,
        e.getMessage(),
        e
      );
    }
  }
}
