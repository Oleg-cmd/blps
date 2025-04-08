package ru.sberbank.sbp.lab2.transfer_service.integration.models;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO для отправки запроса в SBP Adapter
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SbpTransferRequestDto {

  private String senderPhoneNumber;
  private String recipientPhoneNumber;
  private BigDecimal amount;
  private UUID correlationId;
}
