package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.sbp_transfer_service.service.AccountService;
import ru.sberbank.sbp.sbp_transfer_service.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AccountServiceImpl implements AccountService {

    // Имитация базы данных для хранения балансов и зарезервированных сумм
    private final Map<String, BigDecimal> accountBalances = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> reservedAmounts = new ConcurrentHashMap<>();

    // Задержка для имитации асинхронности (в миллисекундах)
    private static final long SIMULATED_DELAY = 1000; // 1 секунда

    /**
     * Проверка достаточности средств на счете
     * Учитывает уже зарезервированные суммы
     */
    @Override
    public boolean hasEnoughBalance(String phoneNumber, BigDecimal amount) {
        log.info("Start checking balance for phone number {}", phoneNumber);
        simulateDelay(); // Имитация задержки
        BigDecimal balance = getBalance(phoneNumber);
        BigDecimal reserved = reservedAmounts.getOrDefault(phoneNumber, BigDecimal.ZERO);
        boolean hasBalance = balance.subtract(reserved).compareTo(amount) >= 0;
        log.info("Finished checking balance for phone number {}, balance sufficient: {}", phoneNumber, hasBalance);
        return hasBalance;
    }

    /**
     * Резервирование средств для перевода
     * Блокирует указанную сумму на счете отправителя
     */
    @Override
    public void reserveFunds(String phoneNumber, BigDecimal amount) {
        log.info("Start reserving {} for phone number {}", amount, phoneNumber);
        if (!hasEnoughBalance(phoneNumber, amount)) {
            throw new InsufficientFundsException("Insufficient funds for transfer");
        }

        simulateDelay(); // Имитация задержки
        reservedAmounts.merge(phoneNumber, amount, BigDecimal::add);
        log.info("Reserved {} for phone number {}", amount, phoneNumber);
        log.info("Finished reserving {} for phone number {}", amount, phoneNumber);
    }

    /**
     * Освобождение зарезервированных средств
     * Используется при отмене или ошибке перевода
     */
    @Override
    public void releaseReservedFunds(String phoneNumber, BigDecimal amount) {
        log.info("Start releasing reserved amount {} for phone number {}", amount, phoneNumber);
        simulateDelay(); // Имитация задержки
        reservedAmounts.computeIfPresent(phoneNumber, (k, v) -> v.subtract(amount));
        log.info("Released reserved amount {} for phone number {}", amount, phoneNumber);
        log.info("Finished releasing reserved amount {} for phone number {}", amount, phoneNumber);
    }

    /**
     * Завершение перевода
     * Списывает средства со счета отправителя и зачисляет получателю
     */
    @Override
    public void completeFundsTransfer(String senderPhone, String recipientPhone,
                                    BigDecimal amount) {
        log.info("Start completing transfer of {} from {} to {}", amount, senderPhone, recipientPhone);
        // Remove reservation
        releaseReservedFunds(senderPhone, amount);

        simulateDelay(); // Имитация задержки

        // Perform actual transfer
        accountBalances.computeIfPresent(senderPhone, (k, v) -> v.subtract(amount));
        accountBalances.merge(recipientPhone, amount, BigDecimal::add);

        log.info("Completed transfer of {} from {} to {}", amount, senderPhone, recipientPhone);
        log.info("Finished completing transfer of {} from {} to {}", amount, senderPhone, recipientPhone);
    }

    private BigDecimal getBalance(String phoneNumber) {
        return accountBalances.getOrDefault(phoneNumber, new BigDecimal("10000.00")); // Default balance for testing
    }

    private void simulateDelay() {
        try {
            Thread.sleep(SIMULATED_DELAY);
        } catch (InterruptedException e) {
            log.warn("Simulated delay interrupted", e);
            Thread.currentThread().interrupt(); // Re-interrupt the thread to respect interruption policy
        }
    }
}