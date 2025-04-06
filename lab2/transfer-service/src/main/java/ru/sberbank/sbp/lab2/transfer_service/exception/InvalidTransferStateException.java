package ru.sberbank.sbp.lab2.transfer_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при попытке выполнить операцию над переводом,
 * находящимся в невалидном для этой операции статусе.
 * Аннотация @ResponseStatus вернет 409 Conflict или 400 Bad Request.
 */
@ResponseStatus(value = HttpStatus.CONFLICT, reason = "Invalid transfer state for operation")
public class InvalidTransferStateException extends TransferBaseException {
    public InvalidTransferStateException(String message) {
        super(message);
    }
}