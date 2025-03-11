package ru.sberbank.sbp.sbp_transfer_service.exception;

public class InsufficientFundsException extends TransferException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}