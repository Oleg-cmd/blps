package ru.sberbank.sbp.lab2.sbp_adapter_service.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.BankInfo;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.SbpTransferRequest;
import ru.sberbank.sbp.lab2.sbp_adapter_service.dto.SbpTransferResponse;
import ru.sberbank.sbp.lab2.sbp_adapter_service.service.SbpAdapterLogic;

@RestController
@RequestMapping("/api/sbp")
@RequiredArgsConstructor
@Validated // Включаем валидацию параметров запроса
public class SbpAdapterController {

  private final SbpAdapterLogic sbpAdapterLogic;

  // Эндпоинт для поиска банка по номеру телефона
  @GetMapping("/banks")
  public ResponseEntity<BankInfo> findBankByPhoneNumber(
    @RequestParam @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber
  ) {
    Optional<BankInfo> bankInfoOptional = sbpAdapterLogic.findBank(phoneNumber);

    // Возвращаем 200 OK с телом, если банк найден, иначе 404 Not Found
    return bankInfoOptional
      .map(ResponseEntity::ok) // Если есть значение, обернуть в ResponseEntity.ok()
      .orElseGet(() -> ResponseEntity.notFound().build()); // Если пусто, вернуть 404
  }

  // Эндпоинт для обработки (имитации) перевода
  @PostMapping("/transfers")
  public ResponseEntity<SbpTransferResponse> processSbpTransfer(
    @Valid @RequestBody SbpTransferRequest request
  ) { // Валидируем тело запроса
    SbpTransferResponse response = sbpAdapterLogic.processTransfer(request);
    // Всегда возвращаем 200 OK, результат операции передается в теле ответа
    return ResponseEntity.ok(response);
  }
}
