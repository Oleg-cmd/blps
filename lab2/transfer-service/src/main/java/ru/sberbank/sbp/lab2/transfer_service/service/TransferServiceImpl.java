package ru.sberbank.sbp.lab2.transfer_service.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferConfirmationResponse;
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReserveFundsCommand;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus;
import ru.sberbank.sbp.lab2.transfer_service.exception.TransferNotFoundException; // Импорт нашего исключения
import ru.sberbank.sbp.lab2.transfer_service.jms.JmsSender;
import ru.sberbank.sbp.lab2.transfer_service.repository.TransferRepository;

@Service // <-- ВАЖНО: Помечаем как Spring Service Bean
@RequiredArgsConstructor // Создаст конструктор для final полей
@Slf4j
public class TransferServiceImpl implements TransferService { // Реализуем интерфейс

  private final TransferRepository transferRepository;
  private final JmsSender jmsSender;
  // private final SbpSystemService sbpSystemService; // Добавим позже
  // private final NotificationService notificationService; // Добавим позже
  // private final PhoneValidationService phoneValidationService; // Добавим позже

  private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(
    "150000.00"
  ); // Пример лимита

  @Override
  @Transactional // Пока Spring @Transactional
  public TransferInitiationResponse initiateTransfer(
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount,
    String recipientBankId
  ) {
    log.info(
      "[TransferService] Initiating transfer from {} to {} amount {}",
      senderPhoneNumber,
      recipientPhoneNumber,
      amount
    );

    // TODO: Валидация номера (PhoneValidationService)
    // TODO: Проверка банка получателя (SbpSystemService)
    String recipientBankName = "Mock Bank for " + recipientBankId; // Заглушка
    // TODO: Проверка лимитов

    // 1. Создаем перевод в статусе PENDING
    Transfer transfer = Transfer.builder()
      .senderPhoneNumber(senderPhoneNumber)
      .recipientPhoneNumber(recipientPhoneNumber)
      .amount(amount)
      .recipientBankId(recipientBankId)
      .recipientBankName(recipientBankName)
      .status(TransferStatus.PENDING) // Начальный статус - PENDING
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

    // 2. Отправляем команду на резервирование средств в account-service
    ReserveFundsCommand command = ReserveFundsCommand.builder()
      .phoneNumber(senderPhoneNumber)
      .amount(amount)
      .correlationId(savedTransfer.getId()) // Связываем с ID перевода
      .build();
    try {
      jmsSender.sendAccountCommand(command); // Отправляем команду
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
      // Если отправка не удалась, транзакция должна откатиться.
      // Бросаем RuntimeException, чтобы @Transactional сработал на откат.
      // Позже JTA обработает это автоматически.
      throw new RuntimeException(
        "Failed to initiate transfer due to JMS send error for id: " +
        savedTransfer.getId(),
        e
      );
    }

    // 3. Обновляем статус перевода на AWAITING_CONFIRMATION
    // (Этот шаг и предыдущий должны быть атомарны с резервированием в account-service -> JTA)
    savedTransfer.setStatus(TransferStatus.AWAITING_CONFIRMATION);
    transferRepository.save(savedTransfer); // Сохраняем обновленный статус
    log.info(
      "[TransferService] Transfer status updated to AWAITING_CONFIRMATION for id: {}",
      savedTransfer.getId()
    );

    // TODO: 4. Отправка кода подтверждения (NotificationService)
    log.warn("[TransferService] Skipping sending confirmation code!");

    return new TransferInitiationResponse(
      savedTransfer.getId(),
      savedTransfer.getStatus(),
      savedTransfer.getRecipientBankName()
    );
  }

  @Override
  @Transactional // Вешаем транзакцию (пока Spring)
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

    // ----- УПРОЩЕННАЯ ЛОГИКА ПОДТВЕРЖДЕНИЯ -----
    if (transfer.getStatus() != TransferStatus.AWAITING_CONFIRMATION) {
      log.warn(
        "[TransferService] Transfer {} is not awaiting confirmation (status: {})",
        transferId,
        transfer.getStatus()
      );
      // В реальном приложении бросить бы исключение InvalidTransferStateException
      return new TransferConfirmationResponse(
        transferId,
        transfer.getStatus(),
        "Transfer is not awaiting confirmation"
      );
    }

    if (!transfer.getConfirmationCode().equals(confirmationCode)) {
      log.warn(
        "[TransferService] Invalid confirmation code for transfer {}",
        transferId
      );
      // TODO: Обработка неверного кода (счетчик попыток)
      return new TransferConfirmationResponse(
        transferId,
        transfer.getStatus(),
        "Invalid confirmation code"
      );
    }

    // TODO: Взаимодействие с SbpSystemService
    log.warn("[TransferService] Skipping SBP processing!");
    boolean sbpSuccess = true; // Эмуляция успеха

    if (sbpSuccess) {
      // TODO: Отправка команды CompleteTransfer в account-service (через JMS)
      log.warn(
        "[TransferService] Skipping sending CompleteTransfer command to account-service!"
      );

      transfer.setStatus(TransferStatus.SUCCESSFUL);
      transfer.setCompletedAt(LocalDateTime.now());
      transferRepository.save(transfer);
      log.info(
        "[TransferService] Transfer {} status updated to SUCCESSFUL",
        transferId
      );

      // TODO: Отправка уведомления об успехе (NotificationService)
      log.warn("[TransferService] Skipping success notification!");

      return new TransferConfirmationResponse(
        transferId,
        TransferStatus.SUCCESSFUL,
        "Transfer successful (simulated)"
      );
    } else {
      // TODO: Отправка команды ReleaseFunds в account-service (через JMS)
      log.warn(
        "[TransferService] Skipping sending ReleaseFunds command to account-service!"
      );

      transfer.setStatus(TransferStatus.FAILED);
      transfer.setFailureReason("SBP processing failed (simulated)");
      transferRepository.save(transfer);
      log.info(
        "[TransferService] Transfer {} status updated to FAILED",
        transferId
      );

      // TODO: Отправка уведомления об ошибке (NotificationService)
      log.warn("[TransferService] Skipping failure notification!");

      return new TransferConfirmationResponse(
        transferId,
        TransferStatus.FAILED,
        "SBP processing failed (simulated)"
      );
    }
    // -----------------------------------------
  }

  @Override
  @Transactional // Транзакция только на чтение
  public Transfer getTransferStatus(UUID transferId) {
    log.debug("[TransferService] Getting status for transfer {}", transferId);
    return findTransferByIdOrFail(transferId);
  }

  // Вспомогательный метод для поиска или выброса исключения
  private Transfer findTransferByIdOrFail(UUID transferId) {
    return transferRepository
      .findById(transferId)
      .orElseThrow(() -> {
        log.warn("[TransferService] Transfer not found by id: {}", transferId);
        return new TransferNotFoundException(
          "Transfer not found: " + transferId
        );
      });
  }

  // Вспомогательный метод для генерации кода
  private String generateSecureConfirmationCode() {
    // В реальном приложении код должен быть безопаснее и, возможно, храниться отдельно
    return String.format("%06d", new SecureRandom().nextInt(1000000));
  }
  // TODO: Реализовать другие методы интерфейса, если нужно (history, limits...)
  // TODO: Создать необходимые кастомные исключения (InvalidTransferStateException, ...)
}
