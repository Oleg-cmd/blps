package ru.sberbank.sbp.lab2.transfer_service.exception;

/**
 * Базовый класс для всех кастомных Runtime исключений сервиса переводов.
 */
public abstract class TransferBaseException extends RuntimeException {
    public TransferBaseException(String message) {
        super(message);
    }

    public TransferBaseException(String message, Throwable cause) {
        super(message, cause);
    }
}