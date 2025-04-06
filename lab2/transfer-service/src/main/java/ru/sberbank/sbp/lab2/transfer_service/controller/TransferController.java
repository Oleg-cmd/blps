package ru.sberbank.sbp.lab2.transfer_service.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// Импортируем DTO и сервисы из правильных пакетов
import ru.sberbank.sbp.lab2.transfer_service.dto.*;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer; // Пока для возврата статуса
import ru.sberbank.sbp.lab2.transfer_service.service.TransferService; // Интерфейс сервиса

@RestController
@RequestMapping("/api/transfers") // Базовый путь для всех эндпоинтов контроллера
@RequiredArgsConstructor // Внедрение зависимостей через конструктор
@Slf4j
public class TransferController {

  private final TransferService transferService; // Зависимость от интерфейса сервиса

  // --- Эндпоинт инициации перевода ---
  @PostMapping
  public ResponseEntity<TransferInitiationResponse> initiateTransfer(
    @Valid @RequestBody InitiateTransferRequest request, // Валидация тела запроса
    @RequestHeader("X-Phone-Number") String senderPhoneNumber
  ) { // Получение номера из заголовка
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
      request.getAmount(),
      request.getBankId()
    );

    // Возвращаем ответ с ID перевода, статусом и кодом 201 Created
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  // --- Эндпоинт подтверждения перевода ---
  @PostMapping("/{transferId}/confirm") // Путь с ID перевода
  public ResponseEntity<TransferConfirmationResponse> confirmTransfer(
    @PathVariable UUID transferId, // Получение ID из пути
    @Valid @RequestBody ConfirmTransferRequest request, // Валидация тела запроса
    @RequestHeader("X-Phone-Number") String senderPhoneNumber
  ) { // Номер отправителя (пока для логов)
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
      // Можно передать senderPhoneNumber, если сервис будет проверять владельца
    );

    // Возвращаем ответ со статусом и кодом 200 OK
    return ResponseEntity.ok(response);
  }

  // --- Эндпоинт получения статуса перевода ---
  @GetMapping("/{transferId}") // Путь с ID перевода
  public ResponseEntity<Transfer> getTransferStatus(
    // Пока возвращаем всю сущность Transfer
    @PathVariable UUID transferId,
    @RequestHeader("X-Phone-Number") String senderPhoneNumber
  ) { // Номер (пока для логов)
    log.info(
      "Received status request for transferId: {} from: {}",
      transferId,
      senderPhoneNumber
    );

    // Вызов метода сервиса
    Transfer transfer = transferService.getTransferStatus(transferId);

    // TODO: Проверка авторизации (владелец или админ) будет добавлена позже

    // Возвращаем найденный перевод и код 200 OK
    // Если сервис не найдет перевод, он должен бросить исключение (например, TransferNotFoundException),
    // которое будет обработано глобальным обработчиком исключений (создадим позже) и вернет 404.
    return ResponseEntity.ok(transfer);
  }
  // TODO: Добавить эндпоинт для получения истории переводов (GET /api/transfers)
  // @GetMapping
  // public ResponseEntity<List<Transfer>> getTransferHistory(
  //      @RequestHeader("X-Phone-Number") String senderPhoneNumber,
  //      @RequestParam(defaultValue = "0") int page,
  //      @RequestParam(defaultValue = "10") int size) { ... }

}
