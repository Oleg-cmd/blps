package ru.sberbank.sbp.sbp_transfer_service.exception;

public class InvalidTransferStateException extends TransferException {
    public InvalidTransferStateException(String message) {
        super(message);
    }
}