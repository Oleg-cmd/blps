package sbp.transfer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Transfer not found")
public class TransferNotFoundException extends RuntimeException {

  public TransferNotFoundException(String message) {
    super(message);
  }
}
