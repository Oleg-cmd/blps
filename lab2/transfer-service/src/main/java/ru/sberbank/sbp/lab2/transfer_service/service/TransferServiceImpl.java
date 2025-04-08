package ru.sberbank.sbp.lab2.transfer_service.service;

import jakarta.transaction.Transactional; // Используем jakarta @Transactional
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
// Импортируем DTO
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferConfirmationResponse;
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.CompleteTransferCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReleaseFundsCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReserveFundsCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendConfirmationCodeCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendFailureNotificationCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendSuccessNotificationCommand;
// Импортируем сущности и enums
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus;
// Импортируем исключения
import ru.sberbank.sbp.lab2.transfer_service.exception.InvalidInputDataException;
import ru.sberbank.sbp.lab2.transfer_service.exception.InvalidRecipientException;
import ru.sberbank.sbp.lab2.transfer_service.exception.InvalidTransferStateException;
import ru.sberbank.sbp.lab2.transfer_service.exception.TransferLimitExceededException;
import ru.sberbank.sbp.lab2.transfer_service.exception.TransferNotFoundException;
// Интеграция
import ru.sberbank.sbp.lab2.transfer_service.integration.models.BankInfo;
import ru.sberbank.sbp.lab2.transfer_service.integration.models.SbpTransferResponse;
// Импортируем репозиторий и другие сервисы/компоненты
import ru.sberbank.sbp.lab2.transfer_service.jms.JmsSender;
import ru.sberbank.sbp.lab2.transfer_service.repository.TransferRepository;
import ru.sberbank.sbp.lab2.transfer_service.service.PhoneValidationService;
// Импортируем интерфейсы сервисов
import ru.sberbank.sbp.lab2.transfer_service.service.SbpSystemService;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

  private final TransferRepository transferRepository;
  private final JmsSender jmsSender;
  // Внедряем реализации через интерфейсы
  private final SbpSystemService sbpSystemService;
  private final PhoneValidationService phoneValidationService;

  private static final int MAX_CONFIRMATION_ATTEMPTS = 3;
  private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(
    "150000.00"
  );

  @Override
  @Transactional
  public TransferInitiationResponse initiateTransfer(
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount,
    String recipientBankId // Не используется, но оставлен для совместимости с интерфейсом
  ) {
    log.info(
      "[TransferService] Initiating transfer from {} to {} amount {}",
      senderPhoneNumber,
      recipientPhoneNumber,
      amount
    );

    // 1. Валидация номеров
    if (!phoneValidationService.validatePhoneFormat(senderPhoneNumber)) {
      throw new InvalidInputDataException(
        "Invalid sender phone number format: " + senderPhoneNumber
      );
    }
    if (!phoneValidationService.validatePhoneFormat(recipientPhoneNumber)) {
      throw new InvalidInputDataException(
        "Invalid recipient phone number format: " + recipientPhoneNumber
      );
    }
    if (senderPhoneNumber.equals(recipientPhoneNumber)) {
      throw new InvalidInputDataException(
        "Sender and recipient phone numbers cannot be the same."
      );
    }

    // 2. Проверка лимитов (заглушка)
    BigDecimal currentDayAmount = BigDecimal.ZERO; // TODO: Implement limit check
    if (currentDayAmount.add(amount).compareTo(DAILY_TRANSFER_LIMIT) > 0) {
      throw new TransferLimitExceededException(
        "Daily transfer limit exceeded."
      );
    }

    // 3. Поиск банка получателя через SBP Adapter (REST вызов)
    Optional<BankInfo> bankInfoOpt = sbpSystemService.findRecipientBank(
      recipientPhoneNumber
    );
    if (bankInfoOpt.isEmpty()) {
      log.warn(
        "[TransferService] Failed to find recipient bank info via SBP Adapter for phone {}",
        recipientPhoneNumber
      );
      throw new InvalidRecipientException(
        "Recipient's bank not found, does not support SBP, or SBP adapter error."
      );
    }
    BankInfo recipientBank = bankInfoOpt.get();

    // 4. Создаем перевод в статусе PENDING
    Transfer transfer = Transfer.builder()
      .senderPhoneNumber(senderPhoneNumber)
      .recipientPhoneNumber(recipientPhoneNumber)
      .amount(amount)
      .recipientBankId(recipientBank.getBankId())
      .recipientBankName(recipientBank.getBankName())
      .status(TransferStatus.PENDING)
      .createdAt(LocalDateTime.now())
      .confirmationCode(generateSecureConfirmationCode())
      .retryCount(0)
      .build();
    Transfer savedTransfer = transferRepository.save(transfer);
    log.info(
      "[TransferService] Transfer entity created with id: {} status: {}",
      savedTransfer.getId(),
      savedTransfer.getStatus()
    );

    // 5. Отправляем команду на резервирование средств (JMS)
    ReserveFundsCommand reserveCmd = ReserveFundsCommand.builder()
      .phoneNumber(senderPhoneNumber)
      .amount(amount)
      .correlationId(savedTransfer.getId())
      .build();
    try {
      jmsSender.sendReserveFundsCommand(reserveCmd);
      log.info(
        "[TransferService] Sent ReserveFundsCommand for transfer id: {}",
        savedTransfer.getId()
      );
    } catch (Exception e) {
      log.error(
        "[TransferService] Failed to send ReserveFundsCommand for transfer id: {}. Initiating rollback.",
        savedTransfer.getId(),
        e
      );
      throw new RuntimeException(
        "Failed to initiate transfer due to JMS send error for id: " +
        savedTransfer.getId(),
        e
      );
    }

    // 6. Обновляем статус на AWAITING_CONFIRMATION
    savedTransfer.setStatus(TransferStatus.AWAITING_CONFIRMATION);
    transferRepository.save(savedTransfer); // Это сохранение будет частью JTA транзакции
    log.info(
      "[TransferService] Transfer status updated to AWAITING_CONFIRMATION for id: {}",
      savedTransfer.getId()
    );

    // 7. Отправляем команду на отправку кода подтверждения (JMS - Fire-and-forget)
    SendConfirmationCodeCommand codeCmd = SendConfirmationCodeCommand.builder()
      .phoneNumber(senderPhoneNumber)
      .code(savedTransfer.getConfirmationCode())
      .correlationId(savedTransfer.getId())
      .build();
    try {
      // Отправка вне основной транзакции или обработка ошибки без отката
      jmsSender.sendConfirmationCodeCommand(codeCmd);
    } catch (Exception e) {
      log.error(
        "[TransferService] Failed to send SendConfirmationCodeCommand for transfer {}. Continuing...",
        savedTransfer.getId(),
        e
      );
    }

    return new TransferInitiationResponse(
      savedTransfer.getId(),
      savedTransfer.getStatus(),
      savedTransfer.getRecipientBankName()
    );
  }

  @Override
  @Transactional
  public TransferConfirmationResponse confirmTransfer(
    UUID transferId,
    String confirmationCode
  ) {
    log.info(
      "[TransferService] Confirming transfer {} with code {}",
      transferId,
      confirmationCode
    );

    Transfer transfer = findTransferByIdOrFail(transferId);

    // 1. Проверка статуса
    if (transfer.getStatus() != TransferStatus.AWAITING_CONFIRMATION) {
      log.warn(
        "[TransferService] Transfer {} is not awaiting confirmation (status: {})",
        transferId,
        transfer.getStatus()
      );
      throw new InvalidTransferStateException(
        "Transfer is not awaiting confirmation. Current status: " +
        transfer.getStatus()
      );
    }

    // TODO: Проверка времени жизни кода подтверждения

    // 2. Проверка кода
    if (!transfer.getConfirmationCode().equals(confirmationCode)) {
      log.warn(
        "[TransferService] Invalid confirmation code for transfer {}",
        transferId
      );
      transfer.setRetryCount(transfer.getRetryCount() + 1);
      String failureMessage;
      boolean maxAttemptsReached =
        transfer.getRetryCount() >= MAX_CONFIRMATION_ATTEMPTS;

      if (maxAttemptsReached) {
        transfer.setStatus(TransferStatus.FAILED);
        String reason = "Invalid confirmation code - max attempts exceeded";
        transfer.setFailureReason(reason);
        failureMessage = "Invalid confirmation code. Max attempts exceeded.";
        // Транзакция будет включать обновление статуса и отправку команды отмены
        sendReleaseFundsCommand(transfer, reason);
        sendFailureNotificationCommand(transfer); // Уведомление
      } else {
        failureMessage =
          "Invalid confirmation code. Attempts left: " +
          (MAX_CONFIRMATION_ATTEMPTS - transfer.getRetryCount());
        // Просто сохраняем счетчик попыток, статус не меняем
      }
      transferRepository.save(transfer); // Сохраняем изменения (статус или счетчик)
      return new TransferConfirmationResponse(
        transferId,
        transfer.getStatus(),
        failureMessage
      );
    }

    // 3. Код верный - продолжаем

    // 4. Взаимодействие с SBP Adapter (REST вызов)
    SbpTransferResponse sbpResponse = sbpSystemService.processTransferViaSbp(
      transfer
    );

    // Вся последующая логика (обновление БД + отправка JMS) должна быть атомарной
    if (sbpResponse.isSuccess()) {
      // 5. Отправляем команду на завершение в account-service (JMS)
      CompleteTransferCommand completeCmd = CompleteTransferCommand.builder()
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .recipientPhoneNumber(transfer.getRecipientPhoneNumber())
        .amount(transfer.getAmount())
        .correlationId(transfer.getId())
        .build();
      try {
        jmsSender.sendCompleteTransferCommand(completeCmd);
        log.info(
          "[TransferService] Sent CompleteTransferCommand for transfer id: {}",
          transfer.getId()
        );
      } catch (Exception e) {
        log.error(
          "[TransferService] Failed to send CompleteTransferCommand for transfer id: {}. Initiating rollback.",
          transfer.getId(),
          e
        );
        throw new RuntimeException(
          "Failed to confirm transfer due to JMS send error for id: " +
          transfer.getId(),
          e
        );
      }

      // 6. Обновляем статус перевода в локальной БД
      transfer.setStatus(TransferStatus.SUCCESSFUL);
      transfer.setSbpTransactionId(sbpResponse.getSbpTransactionId()); // Сохраняем ID из SBP
      transfer.setCompletedAt(LocalDateTime.now());
      transfer.setRetryCount(0);
      transfer.setFailureReason(null);
      transferRepository.save(transfer); // Будет в JTA транзакции
      log.info(
        "[TransferService] Transfer {} status updated to SUCCESSFUL",
        transferId
      );

      // 7. Отправляем команду уведомления об успехе (JMS Fire-and-forget)
      sendSuccessNotificationCommand(transfer);

      return new TransferConfirmationResponse(
        transferId,
        TransferStatus.SUCCESSFUL,
        "Transfer successful"
      );
    } else {
      // SBP обработка не удалась
      String reason = sbpResponse.getErrorMessage() != null
        ? sbpResponse.getErrorMessage()
        : "SBP processing failed (unknown reason)";
      log.error(
        "[TransferService] SBP processing failed for transfer {}: {}",
        transferId,
        reason
      );
      transfer.setStatus(TransferStatus.FAILED);
      transfer.setFailureReason(reason);

      // 8. Отправляем команду на отмену резерва (JMS)
      sendReleaseFundsCommand(transfer, reason);
      transferRepository.save(transfer); // Будет в JTA транзакции
      log.info(
        "[TransferService] Transfer {} status updated to FAILED due to SBP error",
        transferId
      );

      // 9. Отправляем команду уведомления об ошибке (JMS Fire-and-forget)
      sendFailureNotificationCommand(transfer);

      return new TransferConfirmationResponse(
        transferId,
        TransferStatus.FAILED,
        reason
      );
    }
  }

  @Override
  @Transactional // Убери, если нет модификаций и строгая консистентность не нужна
  public Transfer getTransferStatus(UUID transferId) {
    log.debug("[TransferService] Getting status for transfer {}", transferId);
    return findTransferByIdOrFail(transferId);
  }

  // --- Вспомогательные методы ---

  private Transfer findTransferByIdOrFail(UUID transferId) {
    return transferRepository
      .findById(transferId)
      .orElseThrow(() -> {
        log.warn("[TransferService] Transfer not found by id: {}", transferId);
        return new TransferNotFoundException(
          "Transfer not found with id: " + transferId
        );
      });
  }

  private String generateSecureConfirmationCode() {
    return String.format("%06d", new SecureRandom().nextInt(1000000));
  }

  // Отправка команды отмены резерва
  private void sendReleaseFundsCommand(Transfer transfer, String reason) {
    log.warn(
      "[TransferService] Sending ReleaseFunds command for transfer {} due to: {}",
      transfer.getId(),
      reason
    );
    ReleaseFundsCommand releaseCmd = ReleaseFundsCommand.builder()
      .phoneNumber(transfer.getSenderPhoneNumber())
      .amount(transfer.getAmount())
      .correlationId(transfer.getId())
      .build();
    try {
      // Эта отправка должна быть частью основной JTA транзакции,
      // т.к. если она не удастся, статус FAILED не должен быть закоммичен.
      // Ошибка здесь вызовет RuntimeException благодаря handleSendError в JmsSender.
      jmsSender.sendReleaseFundsCommand(releaseCmd);
    } catch (Exception e) {
      // Если handleSendError не бросает исключение, логируем критическую ошибку
      log.error(
        "[TransferService] CRITICAL: Failed to send ReleaseFundsCommand for transfer {}. Manual intervention might be required. Error: {}",
        transfer.getId(),
        e.getMessage(),
        e
      );
      // Можно либо положиться на handleSendError в JmsSender, который бросает исключение,
      // либо бросить его здесь для явности.
      throw new RuntimeException(
        "Failed to send ReleaseFundsCommand for transfer " + transfer.getId(),
        e
      );
    }
  }

  // Отправка команды уведомления об успехе
  private void sendSuccessNotificationCommand(Transfer transfer) {
    SendSuccessNotificationCommand successCmd =
      SendSuccessNotificationCommand.builder()
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .amount(transfer.getAmount())
        .recipientInfo(
          transfer.getRecipientBankName() +
          "/" +
          transfer.getRecipientPhoneNumber()
        )
        .correlationId(transfer.getId())
        .build();
    try {
      jmsSender.sendSuccessNotificationCommand(successCmd);
    } catch (Exception e) {
      log.error(
        "[TransferService] Failed to send SendSuccessNotificationCommand for transfer {}. Continuing...",
        transfer.getId(),
        e
      );
    }
  }

  // Отправка команды уведомления об ошибке
  private void sendFailureNotificationCommand(Transfer transfer) {
    SendFailureNotificationCommand failCmd =
      SendFailureNotificationCommand.builder()
        .senderPhoneNumber(transfer.getSenderPhoneNumber())
        .amount(transfer.getAmount())
        .reason(transfer.getFailureReason())
        .correlationId(transfer.getId())
        .build();
    try {
      jmsSender.sendFailureNotificationCommand(failCmd);
    } catch (Exception e) {
      log.error(
        "[TransferService] Failed to send SendFailureNotificationCommand for transfer {}. Continuing...",
        transfer.getId(),
        e
      );
    }
  }
}
