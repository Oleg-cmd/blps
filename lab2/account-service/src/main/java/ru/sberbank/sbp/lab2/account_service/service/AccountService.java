package ru.sberbank.sbp.lab2.account_service.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import ru.sberbank.sbp.lab2.account_service.entity.Account;

public interface AccountService {
  /**
   * Проверяет, достаточно ли средств на счете (баланс минус резерв).
   * @param phoneNumber Номер телефона.
   * @param amount Сумма для проверки.
   * @return true, если средств достаточно, иначе false.
   * @throws ru.sberbank.sbp.lab2.account.exception.AccountNotFoundException если счет не найден.
   */
  boolean hasEnoughBalance(String phoneNumber, BigDecimal amount);

  /**
   * Резервирует средства на счете.
   * Увеличивает reservedAmount.
   * @param phoneNumber Номер телефона.
   * @param amount Сумма для резервирования.
   * @throws ru.sberbank.sbp.lab2.account.exception.AccountNotFoundException если счет не найден.
   * @throws ru.sberbank.sbp.lab2.account.exception.InsufficientFundsException если недостаточно средств.
   */
  void reserveFunds(String phoneNumber, BigDecimal amount);

  /**
   * Освобождает ранее зарезервированные средства (используется при отмене перевода).
   * Уменьшает reservedAmount. Не должен падать, если счет не найден.
   * @param phoneNumber Номер телефона.
   * @param amount Сумма для освобождения.
   * @param correlationId ID оригинального перевода для логирования.
   */
  void releaseFunds(String phoneNumber, BigDecimal amount, UUID correlationId);

  /**
   * Завершает перевод: списывает средства с баланса отправителя
   * (уменьшает balance и reservedAmount) и зачисляет на баланс получателя.
   * Этот метод должен быть атомарным для обоих счетов.
   * @param senderPhoneNumber Номер телефона отправителя.
   * @param recipientPhoneNumber Номер телефона получателя.
   * @param amount Сумма перевода.
   * @param correlationId ID оригинального перевода для логирования.
   * @throws ru.sberbank.sbp.lab2.account_service.exception.AccountNotFoundException если счет отправителя не найден.
   * @throws IllegalStateException если зарезервированная сумма отправителя меньше суммы перевода.
   * @throws RuntimeException при других ошибках (например, проблема с созданием счета получателя).
   */
  void completeTransfer(
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount,
    UUID correlationId
  );

  /**
   * Получает текущий баланс счета.
   * @param phoneNumber Номер телефона.
   * @return Текущий баланс.
   * @throws ru.sberbank.sbp.lab2.account.exception.AccountNotFoundException если счет не найден.
   */
  BigDecimal getBalance(String phoneNumber);

  /**
   * Получает текущую зарезервированную сумму.
   * @param phoneNumber Номер телефона.
   * @return Зарезервированная сумма.
   * @throws ru.sberbank.sbp.lab2.account.exception.AccountNotFoundException если счет не найден.
   */
  BigDecimal getReservedAmount(String phoneNumber);
}
