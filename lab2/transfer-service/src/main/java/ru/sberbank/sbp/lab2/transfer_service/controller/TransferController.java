package ru.sberbank.sbp.lab2.transfer_service.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sberbank.sbp.lab2.transfer_service.dto.*;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;
import ru.sberbank.sbp.lab2.transfer_service.service.TransferService;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

  private final TransferService transferService;

  // --- Эндпоинт инициации перевода ---
  @PostMapping
  public ResponseEntity<TransferInitiationResponse> initiateTransfer(
    @Valid @RequestBody InitiateTransferRequest request,
    @RequestHeader("X-Phone-Number") String senderPhoneNumber
  ) {
    log.info(
      "Received transfer initiation request from {} for recipient {} amount {}",
      senderPhoneNumber,
      request.getRecipientPhoneNumber(),
      request.getAmount()
    );

    // Вызов метода сервиса
    TransferInitiationResponse response = transferService.initiateTransfer(
      senderPhoneNumber,
      request.getRecipientPhoneNumber(),
      request.getAmount()
    );

    // Возвращаем ответ с ID перевода, статусом и кодом 201 Created
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  // --- Эндпоинт подтверждения перевода ---
  @PostMapping("/{transferId}/confirm")
  public ResponseEntity<TransferConfirmationResponse> confirmTransfer(
    @PathVariable UUID transferId,
    @Valid @RequestBody ConfirmTransferRequest request,
    @RequestHeader("X-Phone-Number") String senderPhoneNumber
  ) {
    log.info(
      "Received confirmation request for transferId: {} with code: {} from: {}",
      transferId,
      request.getConfirmationCode(),
      senderPhoneNumber
    );

    // Вызов метода сервиса
    TransferConfirmationResponse response = transferService.confirmTransfer(
      transferId,
      request.getConfirmationCode()
    );

    // Возвращаем ответ со статусом и кодом 200 OK
    return ResponseEntity.ok(response);
  }

  // --- Эндпоинт получения статуса перевода ---
  @GetMapping("/{transferId}")
  public ResponseEntity<Transfer> getTransferStatus(
    @PathVariable UUID transferId,
    @RequestHeader("X-Phone-Number") String senderPhoneNumber
  ) {
    log.info(
      "Received status request for transferId: {} from: {}",
      transferId,
      senderPhoneNumber
    );

    // Вызов метода сервиса
    Transfer transfer = transferService.getTransferStatus(transferId);

    // Возвращаем найденный перевод и код 200 OK
    // Если сервис не найдет перевод, он должен бросить исключение
    return ResponseEntity.ok(transfer);
  }
}
