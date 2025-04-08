package ru.sberbank.sbp.lab2.transfer_service.integration.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO для получения ответа от SBP Adapter
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SbpTransferResponse {

  private boolean success;
  private String sbpTransactionId;
  private String errorMessage;
}
