package ru.sberbank.sbp.lab2.transfer_service.service;

import java.math.BigDecimal;
import java.util.UUID;
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferConfirmationResponse;
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;

/**
 * Интерфейс сервиса управления переводами.
 * Определяет основные операции бизнес-логики переводов.
 */
public interface TransferService {
  /**
   * Инициирует новый перевод.
   *
   * @param senderPhoneNumber    Номер телефона отправителя.
   * @param recipientPhoneNumber Номер телефона получателя.
   * @param amount               Сумма перевода.
   * @return Ответ с ID перевода и его начальным статусом.
   * @throws RuntimeException если возникают ошибки валидации, связи с другими сервисами и т.д.
   *                          (позже конкретизируем типы исключений).
   */
  TransferInitiationResponse initiateTransfer(
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount
  );

  /**
   * Подтверждает инициированный перевод с помощью кода.
   *
   * @param transferId       Уникальный идентификатор перевода.
   * @param confirmationCode Код подтверждения, введенный пользователем.
   * @return Ответ с финальным статусом перевода.
   * @throws ru.sberbank.sbp.lab2.transfer_service.exception.TransferNotFoundException если перевод не найден.
   * @throws RuntimeException если возникают другие ошибки (неверный код, таймаут, ошибки СБП и т.д.).
   */
  TransferConfirmationResponse confirmTransfer(
    UUID transferId,
    String confirmationCode
  );

  /**
   * Получает текущий статус перевода по его ID.
   *
   * @param transferId Уникальный идентификатор перевода.
   * @return Сущность Transfer с актуальной информацией.
   * @throws ru.sberbank.sbp.lab2.transfer_service.exception.TransferNotFoundException если перевод не найден.
   */
  Transfer getTransferStatus(UUID transferId);
}
