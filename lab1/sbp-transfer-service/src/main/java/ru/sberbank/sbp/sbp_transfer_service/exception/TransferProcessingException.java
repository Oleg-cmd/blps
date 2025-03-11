package ru.sberbank.sbp.sbp_transfer_service.exception;

public class TransferProcessingException extends TransferException {
    public TransferProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}