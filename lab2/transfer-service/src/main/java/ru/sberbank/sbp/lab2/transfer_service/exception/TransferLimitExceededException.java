package ru.sberbank.sbp.lab2.transfer_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при превышении лимита переводов (например, дневного).
 * Аннотация @ResponseStatus вернет 400 Bad Request.
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Transfer limit exceeded")
public class TransferLimitExceededException extends TransferBaseException {
    public TransferLimitExceededException(String message) {
        super(message);
    }
}