package sbp.transfer.scheduled;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sbp.dto.JmsQueueNames;
import sbp.dto.enums.TransferStatus;
import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.SendFailureNotificationCommand;
import sbp.dto.jms.SendSuccessNotificationCommand;
import sbp.transfer.entity.Transfer;
import sbp.transfer.repository.TransferRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferTasks {

  private final TransferRepository transferRepository;
  private final JmsTemplate jmsTemplate;

  @Value("${scheduler.confirmation.timeout-minutes:15}")
  private long confirmationTimeoutMinutes;

  // Общий таймаут для технических операций
  @Value("${scheduler.processing.general-timeout-minutes:5}")
  private long generalProcessingTimeoutMinutes;

  // Крон для очистки AWAITING_CONFIRMATION
  @Scheduled(
    cron = "${scheduler.cleanup.awaiting-confirmation.cron:0 */5 * * * ?}"
  ) // Каждые 5 минут
  @Transactional(Transactional.TxType.REQUIRED)
  public void cleanupAwaitingConfirmationTransfers() {
    LocalDateTime timeoutThreshold = LocalDateTime.now()
      .minusMinutes(confirmationTimeoutMinutes);
    log.info(
      "Scheduler: Cleaning up AWAITING_CONFIRMATION transfers older than {}",
      timeoutThreshold
    );

    List<Transfer> timedOutTransfers =
      transferRepository.findByStatusAndCreatedAtBefore(
        TransferStatus.AWAITING_CONFIRMATION,
        timeoutThreshold
      );

    if (timedOutTransfers.isEmpty()) {
      log.info(
        "Scheduler: No AWAITING_CONFIRMATION transfers found for cleanup."
      );
      return;
    }
    log.info(
      "Scheduled task: Found {} AWAITING_CONFIRMATION transfers to process for timeout.",
      timedOutTransfers.size()
    );

    for (Transfer transfer : timedOutTransfers) {
      log.warn(
        "[{}] Transfer (DB ID: {}) in AWAITING_CONFIRMATION timed out. Created at: {}",
        transfer.getCorrelationId(),
        transfer.getId(),
        transfer.getCreatedAt()
      );

      transfer.setStatus(TransferStatus.TIMEOUT);
      transfer.setFailureReason(
        "Confirmation timed out after " +
        confirmationTimeoutMinutes +
        " minutes."
      );

      ReleaseFundsCommand releaseCmd = ReleaseFundsCommand.builder()
        .correlationId(transfer.getCorrelationId())
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .amount(transfer.getAmount())
        .isFinalDebit(false)
        .build();
      try {
        jmsTemplate.convertAndSend(
          JmsQueueNames.ACCOUNT_RELEASE_FUNDS_CMD_QUEUE,
          releaseCmd
        );
        log.info(
          "[{}] Sent ReleaseFundsCommand for timed out transfer (DB ID: {}).",
          transfer.getCorrelationId(),
          transfer.getId()
        );
      } catch (Exception e) {
        log.error(
          "[{}] Failed to send ReleaseFundsCommand for timed out transfer (DB ID: {}): {}. Transaction will be rolled back.",
          transfer.getCorrelationId(),
          transfer.getId(),
          e.getMessage(),
          e
        );
        throw new RuntimeException(
          "Failed to send ReleaseFundsCommand for transfer " + transfer.getId(),
          e
        );
      }

      sendFailureNotification(
        transfer.getCorrelationId(),
        transfer.getSenderPhoneNumber(),
        transfer.getAmount(),
        transfer.getFailureReason()
      );

      transferRepository.save(transfer);
      log.info(
        "[{}] Transfer (DB ID: {}) status updated to TIMEOUT and saved.",
        transfer.getCorrelationId(),
        transfer.getId()
      );
    }
    log.info(
      "Scheduler: Finished cleaning up AWAITING_CONFIRMATION transfers."
    );
  }

  // Метод для обработки других "зависших" статусов
  // Запускается, например, каждые 2 минуты
  @Scheduled(cron = "${scheduler.cleanup.processing.cron:0 */2 * * * ?}")
  @Transactional(Transactional.TxType.REQUIRED)
  public void cleanupStuckProcessingTransfers() {
    LocalDateTime timeoutThreshold = LocalDateTime.now()
      .minusMinutes(generalProcessingTimeoutMinutes);
    List<TransferStatus> stuckStatuses = Arrays.asList(
      TransferStatus.PROCESSING_RESERVATION,
      TransferStatus.PROCESSING_FUNDS,
      TransferStatus.PROCESSING_EIS
    );

    List<Transfer> stuckTransfers =
      transferRepository.findByStatusInAndCreatedAtBefore(
        stuckStatuses,
        timeoutThreshold
      );
    if (stuckTransfers.isEmpty()) {
      log.info(
        "Scheduler: No stuck processing transfers (PROCESSING_RESERVATION, PROCESSING_FUNDS, PROCESSING_EIS) found for cleanup using general timeout."
      );
      return;
    }

    // Единый метод для обработки, чтобы избежать дублирования
    processStuckTransfers(stuckTransfers, "Processing timed out.");
  }

  private void processStuckTransfers(
    List<Transfer> transfers,
    String defaultReasonPrefix
  ) {
    if (transfers.isEmpty()) {
      return;
    }
    log.info(
      "Scheduler: Found {} transfers stuck in processing states to handle for timeout.",
      transfers.size()
    );

    for (Transfer transfer : transfers) {
      TransferStatus originalStatus = transfer.getStatus();
      log.warn(
        "[{}] Transfer (DB ID: {}) stuck in {} timed out. Created/Updated At: {}", // Используйте updatedAt если есть
        transfer.getCorrelationId(),
        transfer.getId(),
        originalStatus,
        transfer.getCreatedAt()
      );

      String failureReason =
        defaultReasonPrefix + " Status was " + originalStatus + ".";

      if (originalStatus == TransferStatus.PROCESSING_RESERVATION) {
        transfer.setStatus(TransferStatus.FAILED); // Общий FAILED
        transfer.setFailureReason(failureReason);
        sendFailureNotification(
          transfer.getCorrelationId(),
          transfer.getSenderPhoneNumber(),
          transfer.getAmount(),
          failureReason
        );
      } else if (originalStatus == TransferStatus.PROCESSING_FUNDS) {
        transfer.setStatus(TransferStatus.FUNDS_TRANSFER_FAILED);
        transfer.setFailureReason(failureReason);
        sendFailureNotification(
          transfer.getCorrelationId(),
          transfer.getSenderPhoneNumber(),
          transfer.getAmount(),
          failureReason
        );
      } else if (originalStatus == TransferStatus.PROCESSING_EIS) {
        transfer.setStatus(TransferStatus.EIS_ERROR);
        transfer.setFailureReason(failureReason);
        // Для EIS_ERROR по таймауту, всё равно считаем основной перевод успешным
        sendSuccessNotification(
          transfer.getCorrelationId(),
          transfer.getSenderPhoneNumber(),
          transfer.getAmount(),
          transfer.getRecipientPhoneNumber() +
          " (Bank: " +
          transfer.getRecipientBankId() +
          ")"
        );
      }

      transferRepository.save(transfer);
      log.info(
        "[{}] Transfer (DB ID: {}) status updated to {} due to processing timeout and saved.",
        transfer.getCorrelationId(),
        transfer.getId(),
        transfer.getStatus()
      );
    }
  }

  private void sendFailureNotification(
    UUID correlationId,
    String senderPhoneNumber,
    BigDecimal amount,
    String reason
  ) {
    SendFailureNotificationCommand cmd =
      SendFailureNotificationCommand.builder()
        .correlationId(correlationId)
        .senderPhoneNumber(senderPhoneNumber)
        .amount(amount)
        .reason(reason)
        .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_FAILURE_CMD_QUEUE,
        cmd
      );
      log.info(
        "[{}] Sent SendFailureNotificationCommand for timed out processing. Reason: {}",
        correlationId,
        reason
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendFailureNotificationCommand for timed out processing: {}",
        correlationId,
        e.getMessage(),
        e
      );
    }
  }

  private void sendSuccessNotification(
    UUID correlationId,
    String senderPhoneNumber,
    BigDecimal amount,
    String recipientInfo
  ) {
    SendSuccessNotificationCommand cmd =
      SendSuccessNotificationCommand.builder()
        .correlationId(correlationId)
        .senderPhoneNumber(senderPhoneNumber)
        .amount(amount)
        .recipientInfo(recipientInfo)
        .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_SUCCESS_CMD_QUEUE,
        cmd
      );
      log.info(
        "[{}] Sent SendSuccessNotificationCommand for transfer despite processing timeout (e.g. EIS).",
        correlationId
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send SendSuccessNotificationCommand for transfer with processing timeout: {}",
        correlationId,
        e.getMessage(),
        e
      );
    }
  }
}
