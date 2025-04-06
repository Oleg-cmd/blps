package ru.sberbank.sbp.lab2.transfer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfirmTransferRequest {

  @NotBlank(message = "Код подтверждения обязателен")
  @Size(
    min = 6,
    max = 6,
    message = "Код подтверждения должен состоять из 6 цифр"
  ) // Пример валидации длины
  private String confirmationCode;
}
