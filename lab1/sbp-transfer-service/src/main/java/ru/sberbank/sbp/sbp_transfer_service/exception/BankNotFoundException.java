package ru.sberbank.sbp.sbp_transfer_service.exception;

public class BankNotFoundException extends TransferException {
    public BankNotFoundException(String message) {
        super(message);
    }
}