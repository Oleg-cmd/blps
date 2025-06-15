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

  private UUID correlationId;
  private String senderPhoneNumber;
  private String recipientPhoneNumber; // Номер телефона получателя
  private String recipientEmail; // Email получателя
  private BigDecimal amount;
  private boolean success;
  private String reason;
}
