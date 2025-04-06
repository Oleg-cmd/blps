package ru.sberbank.sbp.lab2.transfer_service.service;

// Импорты DTO и сущности из правильных пакетов
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferConfirmationResponse;
import ru.sberbank.sbp.lab2.transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;

import java.math.BigDecimal;
// import java.util.List; // Раскомментируй, если будешь добавлять историю
import java.util.UUID;

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
     * @param recipientBankId      Идентификатор банка получателя.
     * @return Ответ с ID перевода и его начальным статусом.
     * @throws RuntimeException если возникают ошибки валидации, связи с другими сервисами и т.д.
     *                          (позже конкретизируем типы исключений).
     */
    TransferInitiationResponse initiateTransfer(String senderPhoneNumber, String recipientPhoneNumber,
                                                BigDecimal amount, String recipientBankId);

    /**
     * Подтверждает инициированный перевод с помощью кода.
     *
     * @param transferId       Уникальный идентификатор перевода.
     * @param confirmationCode Код подтверждения, введенный пользователем.
     * @return Ответ с финальным статусом перевода.
     * @throws ru.sberbank.sbp.lab2.transfer_service.exception.TransferNotFoundException если перевод не найден.
     * @throws RuntimeException если возникают другие ошибки (неверный код, таймаут, ошибки СБП и т.д.).
     */
    TransferConfirmationResponse confirmTransfer(UUID transferId, String confirmationCode);

    /**
     * Получает текущий статус перевода по его ID.
     *
     * @param transferId Уникальный идентификатор перевода.
     * @return Сущность Transfer с актуальной информацией.
     * @throws ru.sberbank.sbp.lab2.transfer_service.exception.TransferNotFoundException если перевод не найден.
     */
    Transfer getTransferStatus(UUID transferId);

    /*
     * TODO: Определить методы для получения истории, лимитов и т.д., если требуется.
     *
     * Получает историю переводов для указанного номера телефона с пагинацией.
     * @param phoneNumber Номер телефона пользователя.
     * @param page        Номер страницы (начиная с 0).
     * @param size        Количество записей на странице.
     * @return Список переводов для пользователя.
     *
     * List<Transfer> getUserTransferHistory(String phoneNumber, int page, int size);
     */

}