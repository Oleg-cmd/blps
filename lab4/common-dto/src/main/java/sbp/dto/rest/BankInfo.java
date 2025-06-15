package sbp.dto.rest;

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
