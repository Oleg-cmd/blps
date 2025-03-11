package ru.sberbank.sbp.sbp_transfer_service.exception;

public class TransferLimitExceededException extends TransferException {
    public TransferLimitExceededException(String message) {
        super(message);
    }
}