package ru.sberbank.sbp.lab2.transfer_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое, когда перевод не найден по ID.
 * Аннотация @ResponseStatus автоматически вернет 404 Not Found клиенту.
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Transfer not found") // Добавляем reason
public class TransferNotFoundException extends TransferBaseException { // Наследуемся от базового
    public TransferNotFoundException(String message) {
        super(message);
    }
}