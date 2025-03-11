package ru.sberbank.sbp.sbp_transfer_service.exception;

public class PhoneFormatException extends TransferException {
    public PhoneFormatException(String message) {
        super(message);
    }
}