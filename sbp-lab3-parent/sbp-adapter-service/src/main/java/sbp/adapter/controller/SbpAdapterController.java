package sbp.adapter.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import sbp.adapter.dto.SbpAdapterRequest;
import sbp.adapter.dto.SbpAdapterResponse;
import sbp.adapter.exception.SbpBusinessException;
import sbp.adapter.exception.SbpTechnicalException;
import sbp.adapter.service.SbpAdapterLogic;
import sbp.dto.rest.BankInfo;

@RestController
@RequestMapping("/api/sbp")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SbpAdapterController {

  private final SbpAdapterLogic sbpAdapterLogic;

  @GetMapping("/banks")
  public ResponseEntity<BankInfo> findBankByPhoneNumber(
    @RequestParam @NotBlank @Pattern(
      regexp = "\\d{10}",
      message = "Phone number must be 10 digits"
    ) String phoneNumber
  ) {
    log.info("Received request to find bank for phone number: {}", phoneNumber);
    Optional<BankInfo> bankInfoOptional = sbpAdapterLogic.findBankByPhoneNumber(
      phoneNumber
    );

    return bankInfoOptional
      .map(ResponseEntity::ok) // Если банк найден, вернуть 200 OK с телом BankInfo
      .orElseGet(() -> ResponseEntity.notFound().build()); // Если не найден, вернуть 404 Not Found
  }

  @GetMapping("/banks/{bankId}")
  public ResponseEntity<BankInfo> findBankById(@PathVariable String bankId) {
    log.info("ADAPTER CTRL: findBankById called with bankId: {}", bankId);
    Optional<BankInfo> bankInfoOptional = sbpAdapterLogic.findBankById(bankId);
    return bankInfoOptional
      .map(ResponseEntity::ok)
      .orElseGet(() -> {
        log.warn(
          "ADAPTER CTRL: No bank found for bankId: {}, returning 404",
          bankId
        );
        return ResponseEntity.notFound().build();
      });
  }

  @PostMapping("/transfers")
  public ResponseEntity<SbpAdapterResponse> processSbpTransfer(
    @Valid @RequestBody SbpAdapterRequest request
  ) {
    log.info(
      "Received request to process SBP transfer, CorrelationId: {}",
      request.getCorrelationId()
    );
    try {
      SbpAdapterResponse response = sbpAdapterLogic.processTransfer(request);
      return ResponseEntity.ok(response);
    } catch (SbpBusinessException e) {
      log.warn(
        "Business exception during SBP transfer (CorrelationId: {}): {}",
        request.getCorrelationId(),
        e.getMessage()
      );
      SbpAdapterResponse errorResponse = SbpAdapterResponse.builder()
        .success(false)
        .errorMessage(e.getMessage())
        .build();
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
        errorResponse
      );
    } catch (SbpTechnicalException e) {
      log.error(
        "Technical exception during SBP transfer (CorrelationId: {}): {}",
        request.getCorrelationId(),
        e.getMessage(),
        e
      );
      SbpAdapterResponse errorResponse = SbpAdapterResponse.builder()
        .success(false)
        .errorMessage("SBP technical error: " + e.getMessage())
        .build();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        errorResponse
      );
    } catch (Exception e) {
      log.error(
        "Unexpected exception during SBP transfer (CorrelationId: {}): {}",
        request.getCorrelationId(),
        e.getMessage(),
        e
      );
      SbpAdapterResponse errorResponse = SbpAdapterResponse.builder()
        .success(false)
        .errorMessage("Unexpected internal error in SBP adapter.")
        .build();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        errorResponse
      );
    }
  }
}
