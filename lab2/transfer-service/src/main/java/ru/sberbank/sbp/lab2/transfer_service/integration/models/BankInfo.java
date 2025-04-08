package ru.sberbank.sbp.lab2.transfer_service.integration.models;

import lombok.Data;
import lombok.NoArgsConstructor;

// DTO для получения ответа от SBP Adapter
@Data
@NoArgsConstructor // Нужен для десериализации Jackson
public class BankInfo {

  private String bankId;
  private String bankName;
  private boolean supportsSbp;
}
