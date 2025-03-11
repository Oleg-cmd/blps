package ru.sberbank.sbp.sbp_transfer_service.exception;

public class NotificationException extends TransferException {
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}