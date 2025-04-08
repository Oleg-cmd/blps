package ru.sberbank.sbp.lab2.sbp_adapter_service.service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
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
import ru.sberbank.sbp.lab2.sbp_adapter_service.exception.SbpBusinessException;
import ru.sberbank.sbp.lab2.sbp_adapter_service.exception.SbpTechnicalException;

@Service
@Slf4j
public class SbpAdapterLogic {

  private final Random random = ThreadLocalRandom.current();
  private final Map<String, BankInfo> bankRegistry = new ConcurrentHashMap<>();
  private final Map<String, List<String>> phoneToBankIds =
    new ConcurrentHashMap<>();

  private static final BigDecimal BUSINESS_ERROR_AMOUNT = new BigDecimal(
    "422.00"
  );
  private static final BigDecimal TECHNICAL_ERROR_AMOUNT = new BigDecimal(
    "500.00"
  );

  @PostConstruct
  void initializeMockData() {
    log.info("Initializing SBP Adapter Mock Data...");
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
    );

    phoneToBankIds.put("9993334444", List.of("100000002", "100000001"));
    phoneToBankIds.put("9995556666", List.of("100000004"));
    phoneToBankIds.put("9997778888", List.of("100000003", "100000009"));
    phoneToBankIds.put("9990000009", List.of("100000009"));

    log.info(
      "Mock Data Initialized. Banks: {}, Phones: {}",
      bankRegistry.size(),
      phoneToBankIds.size()
    );
  }

  public Optional<BankInfo> findBank(String phoneNumber) {
    log.debug("SBP Adapter: Request to find bank for {}", phoneNumber);
    simulateNetworkDelay(50, 200);

    if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
      log.warn("SBP Adapter: Invalid phone format {}", phoneNumber);
      return Optional.empty();
    }

    List<String> bankIdList = phoneToBankIds.get(phoneNumber);
    if (bankIdList == null || bankIdList.isEmpty()) {
      log.warn(
        "SBP Adapter: Phone number {} not associated with any bank",
        phoneNumber
      );
      return Optional.empty();
    }

    log.debug(
      "SBP Adapter: Found associated bank IDs for {}: {}",
      phoneNumber,
      bankIdList
    );

    for (String bankId : bankIdList) {
      BankInfo bankInfo = bankRegistry.get(bankId);
      if (bankInfo == null) {
        log.error(
          "SBP Adapter: Inconsistency - phone {} linked to non-existent bankId {}",
          phoneNumber,
          bankId
        );
        continue;
      }
      if (bankInfo.isSupportsSbp()) {
        log.info(
          "SBP Adapter: Found suitable SBP-enabled bank {} ({}) for {}",
          bankInfo.getBankName(),
          bankInfo.getBankId(),
          phoneNumber
        );
        return Optional.of(bankInfo);
      } else {
        log.debug(
          "SBP Adapter: Bank {} for phone {} does not support SBP, checking next...",
          bankInfo.getBankName(),
          phoneNumber
        );
      }
    }

    log.warn(
      "SBP Adapter: No SBP-enabled bank found for phone {}",
      phoneNumber
    );
    return Optional.empty();
  }

  public SbpTransferResponse processTransfer(SbpTransferRequest request)
    throws SbpBusinessException, SbpTechnicalException {
    log.debug(
      "SBP Adapter: Request to process transfer {}",
      request.getCorrelationId()
    );
    simulateNetworkDelay(150, 600);

    String sbpTxId =
      "SBP_TX_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    if (BUSINESS_ERROR_AMOUNT.compareTo(request.getAmount()) == 0) {
      String error = "Forced Business Error (e.g., Limit Exceeded)";
      log.warn(
        "SBP Adapter: Transfer {} processing declined (forced by amount). Reason: {}",
        request.getCorrelationId(),
        error
      );
      throw new SbpBusinessException(error);
    }

    if (TECHNICAL_ERROR_AMOUNT.compareTo(request.getAmount()) == 0) {
      String error = "Forced Technical Error (e.g., SBP Unavailable)";
      log.error(
        "SBP Adapter: Transfer {} processing failed (forced by amount). Error: {}",
        request.getCorrelationId(),
        error
      );
      throw new SbpTechnicalException(error);
    }

    int scenario = random.nextInt(100);

    if (scenario < 90) {
      log.info(
        "SBP Adapter: Transfer {} processed successfully. SBP Tx ID: {}",
        request.getCorrelationId(),
        sbpTxId
      );
      return SbpTransferResponse.builder()
        .success(true)
        .sbpTransactionId(sbpTxId)
        .build();
    } else if (scenario < 95) {
      String error =
        "Recipient bank unavailable (Random Error B" +
        (100 + random.nextInt(99)) +
        ")";

      log.error(
        "SBP Adapter: Transfer {} processing failed. Error: {}",
        request.getCorrelationId(),
        error
      );
      throw new SbpBusinessException(error);
    } else {
      String error =
        "SBP technical error (Random Code S" + (500 + random.nextInt(99)) + ")";

      log.error(
        "SBP Adapter: Transfer {} processing failed. Error: {}",
        request.getCorrelationId(),
        error
      );
      throw new SbpTechnicalException(error);
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
