package ru.sberbank.sbp.lab2.sbp_adapter_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class SbpTransferRequest {

  @NotBlank
  @Pattern(regexp = "\\d{10}")
  private String senderPhoneNumber;

  @NotBlank
  @Pattern(regexp = "\\d{10}")
  private String recipientPhoneNumber;

  @NotNull
  @DecimalMin("0.01")
  private BigDecimal amount;

  @NotNull
  private UUID correlationId; // ID из transfer-service
}
