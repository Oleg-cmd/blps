package ru.sberbank.sbp.sbp_transfer_service.service;

import java.math.BigDecimal;

public interface AccountService {
    boolean hasEnoughBalance(String phoneNumber, BigDecimal amount);
    void reserveFunds(String phoneNumber, BigDecimal amount);
    void releaseReservedFunds(String phoneNumber, BigDecimal amount);
    void completeFundsTransfer(String senderPhone, String recipientPhone, BigDecimal amount);
}