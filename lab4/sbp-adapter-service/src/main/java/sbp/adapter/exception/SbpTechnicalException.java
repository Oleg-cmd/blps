package sbp.adapter.exception;

// Используется для технических проблем на стороне СБП или самого адаптера
// Обычно приводит к HTTP 500 Internal Server Error или 503 Service Unavailable
public class SbpTechnicalException extends RuntimeException {

  public SbpTechnicalException(String message) {
    super(message);
  }

  public SbpTechnicalException(String message, Throwable cause) {
    super(message, cause);
  }
}
