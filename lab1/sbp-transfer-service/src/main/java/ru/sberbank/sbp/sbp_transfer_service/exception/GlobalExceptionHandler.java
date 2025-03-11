package ru.sberbank.sbp.sbp_transfer_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.sberbank.sbp.sbp_transfer_service.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(TransferException.class)
    public ResponseEntity<ErrorResponse> handleTransferException(TransferException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(ex.getMessage()));
    }
}