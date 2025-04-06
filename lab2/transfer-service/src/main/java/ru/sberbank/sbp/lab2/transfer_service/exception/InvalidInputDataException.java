package ru.sberbank.sbp.lab2.transfer_service.exception;

 import org.springframework.http.HttpStatus;
 import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение для общих ошибок валидации входных данных (дополнительно к Bean Validation).
 * Аннотация @ResponseStatus вернет 400 Bad Request.
 */
 @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Invalid input data")
 public class InvalidInputDataException extends TransferBaseException {
     public InvalidInputDataException(String message) {
         super(message);
     }
 }