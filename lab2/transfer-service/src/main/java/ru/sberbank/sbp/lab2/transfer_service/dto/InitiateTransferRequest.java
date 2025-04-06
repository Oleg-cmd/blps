package ru.sberbank.sbp.lab2.transfer_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Data;

@Data // Геттеры, сеттеры, equals, hashCode, toString
public class InitiateTransferRequest {

  @NotBlank(message = "Номер телефона получателя обязателен")
  @Pattern(
    regexp = "\\d{10}",
    message = "Номер телефона получателя должен состоять из 10 цифр"
  )
  private String recipientPhoneNumber;

  @NotBlank(message = "Идентификатор банка получателя обязателен")
  private String bankId; // BIC или другой идентификатор банка получателя

  @NotNull(message = "Сумма перевода обязательна")
  @DecimalMin(value = "0.01", message = "Сумма перевода должна быть больше 0")
  private BigDecimal amount;
}
