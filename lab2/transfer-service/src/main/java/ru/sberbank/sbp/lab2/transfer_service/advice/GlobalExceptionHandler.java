package ru.sberbank.sbp.lab2.transfer_service.advice;

import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import ru.sberbank.sbp.lab2.transfer_service.dto.ErrorResponse;
import ru.sberbank.sbp.lab2.transfer_service.exception.*;

@RestControllerAdvice // Перехватывает исключения со всех @RestController
@Slf4j
public class GlobalExceptionHandler {

  // Обработчик для наших базовых исключений (кроме тех, что с @ResponseStatus)
  @ExceptionHandler(TransferBaseException.class)
  public ResponseEntity<ErrorResponse> handleTransferBaseException(
    TransferBaseException ex,
    WebRequest request
  ) {
    // Ищем аннотацию @ResponseStatus на классе исключения
    ResponseStatus responseStatus = ex
      .getClass()
      .getAnnotation(ResponseStatus.class);
    HttpStatus status = (responseStatus != null)
      ? responseStatus.value()
      : HttpStatus.INTERNAL_SERVER_ERROR; // По умолчанию 500
    String reason = (responseStatus != null &&
        !responseStatus.reason().isEmpty())
      ? responseStatus.reason()
      : status.getReasonPhrase();

    log.error(
      "TransferBaseException caught: Status: {}, Reason: {}, Message: {}",
      status,
      reason,
      ex.getMessage(),
      ex
    );

    ErrorResponse errorResponse = new ErrorResponse(
      Instant.now().toString(),
      status.value(),
      reason, // Используем reason из аннотации или стандартный
      ex.getMessage(), // Сообщение из исключения
      request.getDescription(false).substring(4) // Получаем путь запроса (uri=...)
    );
    return new ResponseEntity<>(errorResponse, status);
  }

  // Обработчик для ошибок валидации (@Valid в контроллере)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST) // Всегда 400 Bad Request
  public ErrorResponse handleValidationExceptions(
    MethodArgumentNotValidException ex,
    WebRequest request
  ) {
    // Собираем все сообщения об ошибках валидации полей
    String errors = ex
      .getBindingResult()
      .getFieldErrors()
      .stream()
      .map(error -> error.getField() + ": " + error.getDefaultMessage())
      .collect(Collectors.joining("; "));
    log.warn("Validation errors: {}", errors);

    return new ErrorResponse(
      Instant.now().toString(),
      HttpStatus.BAD_REQUEST.value(),
      "Validation Failed",
      errors, // Сообщения об ошибках полей
      request.getDescription(false).substring(4)
    );
  }

  // Обработчик для всех остальных непредвиденных исключений
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // Всегда 500
  public ErrorResponse handleAllUncaughtException(
    Exception ex,
    WebRequest request
  ) {
    log.error("Unhandled exception caught: {}", ex.getMessage(), ex);

    return new ErrorResponse(
      Instant.now().toString(),
      HttpStatus.INTERNAL_SERVER_ERROR.value(),
      "Internal Server Error",
      "An unexpected error occurred. Please contact support.", // Общее сообщение
      request.getDescription(false).substring(4)
    );
  }
}
