package sbp.dto.jms;

import java.io.Serializable;
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
public class FundsProcessedEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID correlationId; // ID оригинального перевода (Transfer.id)
  private String senderPhoneNumber;
  private String recipientPhoneNumber;
  private BigDecimal amount;
  private boolean success; // true, если списание и зачисление в AccountService прошли успешно
  private String reason; // Причина неудачи, если success = false
}
