package sbp.transfer.service.handler.impl;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import sbp.dto.JmsQueueNames;
import sbp.dto.enums.TransferStatus;
import sbp.dto.jms.ReserveFundsCommand;
import sbp.dto.rest.BankInfo;
import sbp.dto.rest.InitiateTransferRequest;
import sbp.dto.rest.TransferInitiationResponse;
import sbp.transfer.entity.Transfer;
import sbp.transfer.exception.SbpAdapterException;
import sbp.transfer.integration.SbpAdapterClient;
import sbp.transfer.repository.TransferRepository;
import sbp.transfer.service.handler.TransferInitiationHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferInitiationHandlerImpl
  implements TransferInitiationHandler {

  private final TransferRepository transferRepository;
  private final JmsTemplate jmsTemplate;
  private final SbpAdapterClient sbpAdapterClient;
  private final Random random = new Random();

  @Override
  @Transactional(Transactional.TxType.REQUIRED)
  public TransferInitiationResponse initiateTransfer(
    UUID correlationId,
    String senderPhoneNumber,
    InitiateTransferRequest request
  ) {
    log.info(
      "[{}] Handling transfer initiation: Sender={}, Recipient={}, Amount={}, BankId={}",
      correlationId,
      senderPhoneNumber,
      request.getRecipientPhoneNumber(),
      request.getAmount(),
      request.getBankId()
    );

    // Проверка на дубликат по correlationId (если это важно для идемпотентности на этом этапе)
    Optional<Transfer> existingTransferOpt =
      transferRepository.findByCorrelationId(correlationId);
    if (existingTransferOpt.isPresent()) {
      Transfer existingTransfer = existingTransferOpt.get();
      log.warn(
        "[{}] Duplicate transfer initiation detected. Existing Transfer ID: {}, Status: {}",
        correlationId,
        existingTransfer.getId(), // PK из БД
        existingTransfer.getStatus()
      );
      // Формируем ответ на основе существующего перевода
      String bankName = "Bank " + existingTransfer.getRecipientBankId(); // Заглушка, если имя не сохранено
      // Можно попытаться получить имя банка снова, если оно не было сохранено, или вернуть как есть.
      return new TransferInitiationResponse(
        existingTransfer.getId(), // PK из БД
        existingTransfer.getStatus(),
        bankName
      );
    }

    // 1. Валидация банка получателя через SBP Adapter
    String recipientBankName;
    try {
      Optional<BankInfo> bankInfoOpt = sbpAdapterClient.getBankInfoById(
        request.getBankId()
      );
      if (bankInfoOpt.isPresent()) {
        BankInfo bankInfo = bankInfoOpt.get();
        if (!bankInfo.isSupportsSbp()) {
          String reason = String.format(
            "Recipient bank %s (ID: %s) does not support SBP.",
            bankInfo.getBankName(),
            request.getBankId()
          );
          log.warn("[{}] {}", correlationId, reason);
          // В соответствии с вашим потоком, это должно привести к ошибке на стороне клиента,
          // а не к созданию Transfer с FAILED статусом на этом этапе.
          // Бросаем исключение, которое будет обработано в контроллере/GlobalExceptionHandler.
          throw new IllegalArgumentException(reason);
        }
        recipientBankName = bankInfo.getBankName();
        log.info(
          "[{}] Recipient bank validated: ID='{}', Name='{}', SupportsSBP=true",
          correlationId,
          request.getBankId(),
          recipientBankName
        );
      } else {
        String reason = String.format(
          "Recipient bank with ID %s not found via SBP Adapter.",
          request.getBankId()
        );
        log.warn("[{}] {}", correlationId, reason);
        throw new IllegalArgumentException(reason);
      }
    } catch (SbpAdapterException e) { // Ловим специфичное исключение от клиента адаптера
      String reason = String.format(
        "Failed to communicate with SBP Adapter for bank %s: %s",
        request.getBankId(),
        e.getMessage()
      );
      log.error("[{}] {}", correlationId, reason, e);
      // Перебрасываем как RuntimeException, чтобы откатить транзакцию и вернуть ошибку клиенту
      throw new RuntimeException(reason, e);
    } catch (Exception e) { // Другие неожиданные ошибки при вызове адаптера
      String reason = String.format(
        "Unexpected error while validating recipient bank %s: %s",
        request.getBankId(),
        e.getMessage()
      );
      log.error("[{}] {}", correlationId, reason, e);
      throw new RuntimeException(reason, e);
    }

    // 2. Создание Transfer (status: PENDING)
    Transfer newTransfer = Transfer.builder()
      .correlationId(correlationId) // Устанавливаем correlationId
      .senderPhoneNumber(senderPhoneNumber)
      .recipientPhoneNumber(request.getRecipientPhoneNumber())
      .recipientBankId(request.getBankId())
      // recipientBankName можно было бы сохранить здесь, если SbpAdapterClient его возвращает
      .amount(request.getAmount())
      .status(TransferStatus.PENDING)
      .createdAt(LocalDateTime.now())
      .build();

    // 3. Генерируем userConfirmationCode
    String userConfirmationCode = String.format(
      "%06d",
      random.nextInt(1000000)
    );
    newTransfer.setSbpConfirmationCode(userConfirmationCode);
    log.info(
      "[{}] Generated user confirmation code (masked): {}",
      correlationId,
      maskCode(userConfirmationCode)
    );

    // 4. Сохраняем Transfer (с кодом)
    Transfer savedTransfer = transferRepository.save(newTransfer);
    log.info(
      "[{}] Transfer entity created and saved with DB ID: {}, Status: {}",
      correlationId,
      savedTransfer.getId(),
      savedTransfer.getStatus()
    );

    // 5. Отправляем ReserveFundsCommand в AccountService
    ReserveFundsCommand reserveFundsCmd = ReserveFundsCommand.builder()
      .correlationId(correlationId) // Используем тот же correlationId
      .senderPhoneNumber(senderPhoneNumber)
      .amount(request.getAmount())
      .confirmationCode(userConfirmationCode) // Передаем сгенерированный код
      .build();

    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.ACCOUNT_RESERVE_FUNDS_CMD_QUEUE,
        reserveFundsCmd
      );
      log.info(
        "[{}] Sent ReserveFundsCommand to AccountService. Queue: {}",
        correlationId,
        JmsQueueNames.ACCOUNT_RESERVE_FUNDS_CMD_QUEUE
      );
    } catch (Exception e) {
      log.error(
        "[{}] Failed to send ReserveFundsCommand to JMS: {}",
        correlationId,
        e.getMessage(),
        e
      );
      // Если отправка JMS не удалась, JTA-транзакция откатится,
      // включая сохранение Transfer. Это правильное поведение.
      throw new RuntimeException(
        "Failed to send ReserveFundsCommand to JMS, rolling back transfer initiation",
        e
      );
    }

    // 6. Transfer.status -> PROCESSING_RESERVATION
    savedTransfer.setStatus(TransferStatus.PROCESSING_RESERVATION);
    transferRepository.save(savedTransfer); // Обновляем статус в той же транзакции
    log.info(
      "[{}] Transfer DB ID: {}, Status updated to: {}",
      savedTransfer.getId(),
      savedTransfer.getStatus()
    );

    // 7. Отвечаем клиенту TransferInitiationResponse
    return new TransferInitiationResponse(
      savedTransfer.getId(), // PK сущности Transfer
      savedTransfer.getStatus(),
      recipientBankName // Имя банка, полученное от SBP Adapter
    );
  }

  private String maskCode(String code) {
    if (code == null) return "NULL";
    if (code.length() <= 2) return "****";
    return "****" + code.substring(Math.max(0, code.length() - 2));
  }
}
