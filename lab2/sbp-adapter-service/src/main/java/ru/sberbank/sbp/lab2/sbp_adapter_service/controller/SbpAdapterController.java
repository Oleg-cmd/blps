package ru.sberbank.sbp.lab2.sbp_adapter_service.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.BankInfo;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.SbpTransferRequest;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.SbpTransferResponse;
import ru.sberbank.sbp.lab2.sbp_adapter_service.exception.SbpBusinessException;
import ru.sberbank.sbp.lab2.sbp_adapter_service.exception.SbpTechnicalException;
import ru.sberbank.sbp.lab2.sbp_adapter_service.service.SbpAdapterLogic;

@RestController
@RequestMapping("/api/sbp")
@RequiredArgsConstructor
@Validated
public class SbpAdapterController {

  private final SbpAdapterLogic sbpAdapterLogic;

  @GetMapping("/banks")
  public ResponseEntity<BankInfo> findBankByPhoneNumber(
    @RequestParam @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber
  ) {
    Optional<BankInfo> bankInfoOptional = sbpAdapterLogic.findBank(phoneNumber);
    return bankInfoOptional
      .map(ResponseEntity::ok)
      .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/transfers")
  public ResponseEntity<SbpTransferResponse> processSbpTransfer(
    @Valid @RequestBody SbpTransferRequest request
  ) {
    try {
      SbpTransferResponse response = sbpAdapterLogic.processTransfer(request);
      // Успех (даже если success=false из-за бизнес-правил) возвращаем 200 OK
      return ResponseEntity.ok(response);
    } catch (SbpBusinessException e) {
      // Ожидаемая бизнес-ошибка (банк недоступен, отказ по правилам) - 422
      SbpTransferResponse errorResponse = SbpTransferResponse.builder()
        .success(false)
        .errorMessage(e.getMessage())
        .build();
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
        errorResponse
      );
    } catch (SbpTechnicalException e) {
      // Техническая ошибка SBP или адаптера - 500 или 503
      SbpTransferResponse errorResponse = SbpTransferResponse.builder()
        .success(false)
        .errorMessage(e.getMessage())
        .build();

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        errorResponse
      );
    }
  }
}
