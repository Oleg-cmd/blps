package ru.sberbank.sbp.sbp_transfer_service.exception;

public class TransferNotFoundException extends TransferException {
    public TransferNotFoundException(String message) {
        super(message);
    }
}