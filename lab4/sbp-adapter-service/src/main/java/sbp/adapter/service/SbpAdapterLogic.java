package sbp.adapter.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbp.adapter.config.MockSbpDataConfig;
import sbp.adapter.dto.SbpAdapterRequest;
import sbp.adapter.dto.SbpAdapterResponse;
import sbp.adapter.exception.SbpBusinessException;
import sbp.adapter.exception.SbpTechnicalException;
import sbp.adapter.util.MockUtils;
import sbp.dto.rest.BankInfo;

@Service
@Slf4j
@RequiredArgsConstructor
public class SbpAdapterLogic {

  private final MockSbpDataConfig mockConfig;

  public Optional<BankInfo> findBankByPhoneNumber(String phoneNumber) {
    log.debug(
      "SBP Adapter: Request to find bank for phone number: {}",
      phoneNumber
    );
    MockUtils.simulateNetworkDelay(50, 200);

    if (MockSbpDataConfig.UNKNOWN_PHONE_FOR_BANK_SEARCH.equals(phoneNumber)) {
      log.info(
        "SBP Adapter: Phone number {} configured to not find any bank.",
        phoneNumber
      );
      return Optional.empty();
    }

    List<String> associatedBankIds = mockConfig.getBankIdsByPhone(phoneNumber);
    if (associatedBankIds == null || associatedBankIds.isEmpty()) {
      log.warn(
        "SBP Adapter: Phone number {} not associated with any bank in mock data.",
        phoneNumber
      );
      return Optional.empty();
    }

    for (String bankId : associatedBankIds) {
      BankInfo bankInfo = mockConfig.getBankById(bankId);
      if (bankInfo != null && bankInfo.isSupportsSbp()) {
        log.info(
          "SBP Adapter: Found SBP-enabled bank {} ({}) for phone {}",
          bankInfo.getBankName(),
          bankInfo.getBankId(),
          phoneNumber
        );
        return Optional.of(bankInfo);
      }
    }

    log.warn(
      "SBP Adapter: No SBP-enabled bank found for phone {} among associated banks: {}",
      phoneNumber,
      associatedBankIds
    );
    return Optional.empty();
  }

  public Optional<BankInfo> findBankById(String bankId) {
    log.debug("SBP Adapter: Request to find bank by ID: {}", bankId);
    MockUtils.simulateNetworkDelay(30, 150);

    BankInfo bankInfo = mockConfig.getBankById(bankId); // Используем существующий метод из MockSbpDataConfig

    if (bankInfo != null) {
      log.info(
        "SBP Adapter: Found bank {} for ID {}",
        bankInfo.getBankName(),
        bankId
      );
      return Optional.of(bankInfo);
    } else {
      log.warn("SBP Adapter: Bank with ID {} not found in mock data.", bankId);
      return Optional.empty();
    }
  }

  public SbpAdapterResponse processTransfer(SbpAdapterRequest request)
    throws SbpBusinessException, SbpTechnicalException {
    log.debug(
      "SBP Adapter: Request to process transfer, CorrelationId: {}",
      request.getCorrelationId()
    );
    MockUtils.simulateNetworkDelay(150, 600);

    if (
      MockSbpDataConfig.BUSINESS_ERROR_AMOUNT.compareTo(request.getAmount()) ==
      0
    ) {
      handleForcedBusinessError(request.getCorrelationId());
    }
    if (
      MockSbpDataConfig.TECHNICAL_ERROR_AMOUNT.compareTo(request.getAmount()) ==
      0
    ) {
      handleForcedTechnicalError(request.getCorrelationId());
    }

    return generateRandomOutcome(request.getCorrelationId());
  }

  private void handleForcedBusinessError(UUID correlationId)
    throws SbpBusinessException {
    String error =
      "Forced Business Error from SBP Adapter (e.g., SBP Limit Exceeded)";
    log.warn(
      "SBP Adapter: Transfer {} (CorrelationId) processing declined (forced by amount). Reason: {}",
      correlationId,
      error
    );
    throw new SbpBusinessException(error);
  }

  private void handleForcedTechnicalError(UUID correlationId)
    throws SbpTechnicalException {
    String error =
      "Forced Technical Error from SBP Adapter (e.g., SBP System Unavailable)";
    log.error(
      "SBP Adapter: Transfer {} (CorrelationId) processing failed (forced by amount). Error: {}",
      correlationId,
      error
    );
    throw new SbpTechnicalException(error);
  }

  private SbpAdapterResponse generateRandomOutcome(UUID correlationId)
    throws SbpBusinessException, SbpTechnicalException {
    String sbpTxId =
      "SBP_TX_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    int scenarioRoll = MockUtils.getRandomInt(100); // 0-99

    if (scenarioRoll < mockConfig.getSuccessOutcomeWeight()) {
      log.info(
        "SBP Adapter: Transfer {} (CorrelationId) processed successfully. SBP Tx ID: {}",
        correlationId,
        sbpTxId
      );
      return SbpAdapterResponse.builder()
        .success(true)
        .sbpTransactionId(sbpTxId)
        .build();
    } else if (
      scenarioRoll <
      mockConfig.getSuccessOutcomeWeight() +
      mockConfig.getBusinessErrorOutcomeWeight()
    ) {
      String error =
        "Recipient bank rejected transfer (Random SBP Business Error B" +
        (100 + MockUtils.getRandomInt(900)) +
        ")";
      log.warn(
        "SBP Adapter: Transfer {} (CorrelationId) processing failed. Business Error: {}",
        correlationId,
        error
      );
      throw new SbpBusinessException(error);
    } else {
      String error =
        "SBP technical malfunction (Random SBP Technical Error S" +
        (500 + MockUtils.getRandomInt(500)) +
        ")";
      log.error(
        "SBP Adapter: Transfer {} (CorrelationId) processing failed. Technical Error: {}",
        correlationId,
        error
      );
      throw new SbpTechnicalException(error);
    }
  }
}
