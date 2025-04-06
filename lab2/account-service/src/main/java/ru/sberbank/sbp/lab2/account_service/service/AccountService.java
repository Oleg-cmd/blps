package ru.sberbank.sbp.lab2.account_service.service;

import ru.sberbank.sbp.lab2.account_service.entity.Account;

import java.math.BigDecimal;
import java.util.Optional;

public interface AccountService {

    /**
     * Находит аккаунт по номеру телефона. Создает новый, если не найден.
     * @param phoneNumber Номер телефона (10 цифр).
     * @return Найденный или созданный аккаунт.
     */
    Account findOrCreateAccount(String phoneNumber);

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
     * Освобождает ранее зарезервированные средства.
     * Уменьшает reservedAmount.
     * @param phoneNumber Номер телефона.
     * @param amount Сумма для освобождения.
     * @throws ru.sberbank.sbp.lab2.account.exception.AccountNotFoundException если счет не найден.
     */
    void releaseReservedFunds(String phoneNumber, BigDecimal amount);

    /**
     * Завершает перевод: списывает средства с баланса отправителя
     * (уменьшает balance и reservedAmount) и зачисляет на баланс получателя.
     * @param senderPhoneNumber Номер телефона отправителя.
     * @param recipientPhoneNumber Номер телефона получателя.
     * @param amount Сумма перевода.
     * @throws ru.sberbank.sbp.lab2.account.exception.AccountNotFoundException если счет отправителя или получателя не найден.
     */
    void completeFundsTransfer(String senderPhoneNumber, String recipientPhoneNumber, BigDecimal amount);

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