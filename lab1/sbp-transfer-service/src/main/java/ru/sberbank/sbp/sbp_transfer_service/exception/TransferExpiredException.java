package ru.sberbank.sbp.sbp_transfer_service.exception;

public class TransferExpiredException extends TransferException {
    public TransferExpiredException(String message) {
        super(message);
    }
}