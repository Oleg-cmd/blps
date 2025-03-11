package ru.sberbank.sbp.sbp_transfer_service.exception;

public class SbpSystemException extends TransferException {
    public SbpSystemException(String message) {
        super(message);
    }
}