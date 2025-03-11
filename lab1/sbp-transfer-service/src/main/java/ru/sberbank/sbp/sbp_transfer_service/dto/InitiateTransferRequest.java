package ru.sberbank.sbp.sbp_transfer_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InitiateTransferRequest {
    @NotBlank(message = "Номер телефона получателя обязателен")
    @Pattern(regexp = "\\d{10}", message = "Номер телефона должен состоять из 10 цифр")
    private String recipientPhoneNumber;

    @NotBlank(message = "Идентификатор банка получателя обязателен")
    private String bankId;

    @DecimalMin(value = "0.01", message = "Сумма перевода должна быть больше 0")
    private BigDecimal amount;
}