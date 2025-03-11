package ru.sberbank.sbp.sbp_transfer_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmTransferRequest {
    @NotBlank(message = "Код подтверждения обязателен")
    private String confirmationCode;
}