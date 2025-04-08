package ru.sberbank.sbp.lab2.sbp_adapter_service.service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.BankInfo;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.SbpTransferRequest;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.SbpTransferResponse;

@Service
@Slf4j
public class SbpAdapterLogic {

  private final Random random = ThreadLocalRandom.current();
  // Простой реестр банков и их статус поддержки СБП (имитация)
  private final Map<String, BankInfo> bankRegistry = new ConcurrentHashMap<>();
  // Простой реестр "привязки" номеров к банку по умолчанию (имитация)
  private final Map<String, String> phoneToDefaultBankId =
    new ConcurrentHashMap<>();

  // Инициализация заглушечных данных при старте сервиса
  @PostConstruct
  void initializeMockData() {
    log.info("Initializing SBP Adapter Mock Data...");
    // Добавляем несколько банков
    bankRegistry.put(
      "100000001",
      new BankInfo("100000001", "Alfa-Bank (Mock)", true)
    );
    bankRegistry.put(
      "100000002",
      new BankInfo("100000002", "SberBank (Mock)", true)
    );
    bankRegistry.put(
      "100000003",
      new BankInfo("100000003", "VTB (Mock)", true)
    );
    bankRegistry.put(
      "100000004",
      new BankInfo("100000004", "Tinkoff (Mock)", true)
    );
    bankRegistry.put(
      "100000009",
      new BankInfo("100000009", "Closed Bank (Mock)", false)
    ); // Банк не поддерживает СБП

    // Привязываем некоторые номера к банкам
    phoneToDefaultBankId.put("9993334444", "100000002"); // Получатель из нашего теста -> SberBank
    phoneToDefaultBankId.put("9995556666", "100000004"); // -> Tinkoff
    phoneToDefaultBankId.put("9997778888", "100000001"); // -> Alfa-Bank
    phoneToDefaultBankId.put("9990000009", "100000009"); // -> Closed Bank
    log.info(
      "Mock Data Initialized. Banks: {}, Phones: {}",
      bankRegistry.size(),
      phoneToDefaultBankId.size()
    );
  }

  // Имитация поиска банка по номеру
  public Optional<BankInfo> findBank(String phoneNumber) {
    log.debug("SBP Adapter: Request to find bank for {}", phoneNumber);
    simulateNetworkDelay(50, 200);

    if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
      log.warn("SBP Adapter: Invalid phone format {}", phoneNumber);
      return Optional.empty();
    }

    // Ищем банк по умолчанию для этого номера
    String defaultBankId = phoneToDefaultBankId.get(phoneNumber);
    if (defaultBankId == null) {
      log.warn(
        "SBP Adapter: Phone number {} not registered for SBP default bank",
        phoneNumber
      );
      return Optional.empty(); // Номер не привязан
    }

    // Находим информацию о банке
    BankInfo bankInfo = bankRegistry.get(defaultBankId);
    if (bankInfo == null) {
      // Странная ситуация: номер привязан к несуществующему банку
      log.error(
        "SBP Adapter: Inconsistency - phone {} linked to non-existent bankId {}",
        phoneNumber,
        defaultBankId
      );
      return Optional.empty();
    }

    // Проверяем, поддерживает ли банк СБП
    if (!bankInfo.isSupportsSbp()) {
      log.warn(
        "SBP Adapter: Bank {} for phone {} does not support SBP",
        bankInfo.getBankName(),
        phoneNumber
      );
      // Возвращаем информацию о банке, но с флагом false? Или empty?
      // СБП обычно не возвращает неподдерживающие банки. Вернем empty.
      return Optional.empty();
    }

    log.info(
      "SBP Adapter: Found default bank {} ({}) for {}",
      bankInfo.getBankName(),
      bankInfo.getBankId(),
      phoneNumber
    );
    return Optional.of(bankInfo);
  }

  // Имитация обработки перевода (остается со случайными ошибками)
  public SbpTransferResponse processTransfer(SbpTransferRequest request) {
    log.debug(
      "SBP Adapter: Request to process transfer {}",
      request.getCorrelationId()
    );
    simulateNetworkDelay(150, 600);

    String sbpTxId =
      "SBP_TX_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    int scenario = random.nextInt(100);

    // Можно добавить зависимость от банка получателя из request, если он там будет
    // String recipientBankId = bankRegistry.get(phoneToDefaultBankId.get(request.getRecipientPhoneNumber())).getBankId();
    // if (recipientBankId.equals("ID_ПРОБЛЕМНОГО_БАНКА")) { scenario = 80; } // Увеличить шанс ошибки

    if (scenario < 75) { // 75% шанс успеха
      log.info(
        "SBP Adapter: Transfer {} processed successfully. SBP Tx ID: {}",
        request.getCorrelationId(),
        sbpTxId
      );
      return SbpTransferResponse.builder()
        .success(true)
        .sbpTransactionId(sbpTxId)
        .build();
    } else if (scenario < 85) { // 10% шанс - ошибка банка получателя
      String error =
        "Recipient bank unavailable (Error B" +
        (100 + random.nextInt(99)) +
        ")";
      log.error(
        "SBP Adapter: Transfer {} processing failed. Error: {}",
        request.getCorrelationId(),
        error
      );
      return SbpTransferResponse.builder()
        .success(false)
        .errorMessage(error)
        .build();
    } else if (scenario < 95) { // 10% шанс - техническая ошибка СБП
      String error =
        "SBP technical error (Code S" + (500 + random.nextInt(99)) + ")";
      log.error(
        "SBP Adapter: Transfer {} processing failed. Error: {}",
        request.getCorrelationId(),
        error
      );
      return SbpTransferResponse.builder()
        .success(false)
        .errorMessage(error)
        .build();
    } else { // 5% шанс - отказ по лимитам/правилам
      String error = "Transfer declined by SBP rules (Limit exceeded or other)";
      log.warn(
        "SBP Adapter: Transfer {} processing declined. Reason: {}",
        request.getCorrelationId(),
        error
      );
      return SbpTransferResponse.builder()
        .success(false)
        .errorMessage(error)
        .build();
    }
  }

  private void simulateNetworkDelay(long minMillis, long maxMillis) {
    try {
      long delay = minMillis + random.nextLong(maxMillis - minMillis + 1);
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("SBP Adapter: Network delay simulation interrupted");
    }
  }
}
