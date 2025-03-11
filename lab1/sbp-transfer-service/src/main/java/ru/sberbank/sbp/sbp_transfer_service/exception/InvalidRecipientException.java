package ru.sberbank.sbp.sbp_transfer_service.exception;

public class InvalidRecipientException extends TransferException {
    public InvalidRecipientException(String message) {
        super(message);
    }
}