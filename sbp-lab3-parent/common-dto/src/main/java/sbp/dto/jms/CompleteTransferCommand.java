package sbp.dto.jms;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteTransferCommand {

  private UUID correlationId; // Для сквозного отслеживания
  private String senderPhoneNumber; // Номер телефона отправителя
  private String recipientPhoneNumber; // Номер телефона получателя
  private BigDecimal amount; // Сумма перевода
}
