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
public class ReleaseFundsCommand implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID correlationId; // ID оригинального перевода (Transfer.id)
  private String senderPhoneNumber; // Чьи средства освободить/списать с резерва
  private String recipientPhoneNumber; // Кому зачислить (если это часть завершения перевода)
  private BigDecimal amount; // Сумма
  private boolean isFinalDebit; // Флаг, указывающий, является ли это финальным списанием с резерва и зачислением получателю (true), или просто отменой резерва (false).
}
