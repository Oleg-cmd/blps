package ru.sberbank.sbp.lab2.transfer_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
  value = HttpStatus.BAD_REQUEST,
  reason = "Invalid recipient or recipient bank unavailable via SBP"
)
public class InvalidRecipientException extends TransferBaseException {

  public InvalidRecipientException(String message) {
    super(message);
  }

  public InvalidRecipientException(String message, Throwable cause) {
    super(message, cause);
  }
}
