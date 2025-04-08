package ru.sberbank.sbp.lab2.sbp_adapter_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SbpTransferResponse {

  private boolean success;
  private String sbpTransactionId; // ID операции в "СБП"
  private String errorMessage;
}
