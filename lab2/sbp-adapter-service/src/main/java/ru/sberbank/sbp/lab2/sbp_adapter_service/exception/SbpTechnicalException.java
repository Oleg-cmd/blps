package ru.sberbank.sbp.lab2.sbp_adapter_service.exception;

public class SbpTechnicalException extends RuntimeException {

  public SbpTechnicalException(String message) {
    super(message);
  }

  public SbpTechnicalException(String message, Throwable cause) {
    super(message, cause);
  }
}
