package sbp.dto.rest;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sbp.dto.enums.TransferStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferInitiationResponse {

  private UUID transferId;
  private TransferStatus status;
  private String recipientBankName;
}
