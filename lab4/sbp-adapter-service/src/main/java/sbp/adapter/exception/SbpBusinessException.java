package sbp.adapter.exception;

// Используется для ожидаемых бизнес-ошибок (например, банк отклонил операцию)
// Обычно приводит к HTTP 422 Unprocessable Entity или 400 Bad Request
public class SbpBusinessException extends Exception {

  public SbpBusinessException(String message) {
    super(message);
  }
}
