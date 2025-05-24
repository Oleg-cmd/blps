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
public class SendSuccessNotificationCommand implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID correlationId; // ID оригинального перевода
  private String senderPhoneNumber; // Кому отправлять уведомление
  private BigDecimal amount; // Сумма успешного перевода
  private String recipientInfo; // Информация о получателе (например, "банк / номер тел.")
}
