package ru.sberbank.sbp.lab2.sbp_adapter_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankInfo {

  private String bankId;
  private String bankName;
  private boolean supportsSbp;
}
