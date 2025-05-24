package sbp.dto.rest;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import sbp.dto.enums.TransferStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferConfirmationResponse {

  private UUID transferId;
  private TransferStatus status; // Финальный или промежуточный статус после попытки подтверждения
  private String message; // Сообщение об успехе, ошибке или количестве оставшихся попыток
}
