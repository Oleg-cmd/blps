package sbp.transfer.exception;

public class SbpAdapterException extends RuntimeException {

  public SbpAdapterException(String message) {
    super(message);
  }

  public SbpAdapterException(String message, Throwable cause) {
    super(message, cause);
  }
}
