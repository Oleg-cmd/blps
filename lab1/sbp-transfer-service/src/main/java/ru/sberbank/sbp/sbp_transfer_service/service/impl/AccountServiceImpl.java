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
    
    // Simulate account balances (in production would use proper database)
    private final Map<String, BigDecimal> accountBalances = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> reservedAmounts = new ConcurrentHashMap<>();

    @Override
    public boolean hasEnoughBalance(String phoneNumber, BigDecimal amount) {
        BigDecimal balance = getBalance(phoneNumber);
        BigDecimal reserved = reservedAmounts.getOrDefault(phoneNumber, BigDecimal.ZERO);
        return balance.subtract(reserved).compareTo(amount) >= 0;
    }

    @Override
    public void reserveFunds(String phoneNumber, BigDecimal amount) {
        if (!hasEnoughBalance(phoneNumber, amount)) {
            throw new InsufficientFundsException("Insufficient funds for transfer");
        }
        
        reservedAmounts.merge(phoneNumber, amount, BigDecimal::add);
        log.info("Reserved {} for phone number {}", amount, phoneNumber);
    }

    @Override
    public void releaseReservedFunds(String phoneNumber, BigDecimal amount) {
        reservedAmounts.computeIfPresent(phoneNumber, (k, v) -> v.subtract(amount));
        log.info("Released reserved amount {} for phone number {}", amount, phoneNumber);
    }

    @Override
    public void completeFundsTransfer(String senderPhone, String recipientPhone, 
            BigDecimal amount) {
        // Remove reservation
        releaseReservedFunds(senderPhone, amount);
        
        // Perform actual transfer
        accountBalances.computeIfPresent(senderPhone, (k, v) -> v.subtract(amount));
        accountBalances.merge(recipientPhone, amount, BigDecimal::add);
        
        log.info("Completed transfer of {} from {} to {}", amount, senderPhone, recipientPhone);
    }

    private BigDecimal getBalance(String phoneNumber) {
        return accountBalances.getOrDefault(phoneNumber, new BigDecimal("10000.00")); // Default balance for testing
    }
}