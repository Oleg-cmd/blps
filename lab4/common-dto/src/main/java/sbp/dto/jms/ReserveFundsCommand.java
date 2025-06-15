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
public class ReserveFundsCommand implements Serializable {

  private static final long serialVersionUID = 1L; // Важно для Serializable объектов, передаваемых по JMS

  private UUID correlationId; // ID оригинального перевода (Transfer.id)
  private String senderPhoneNumber; // Номер телефона пользователя, чьи средства нужно зарезервировать
  private BigDecimal amount; // Сумма для резервирования
  private String confirmationCode;
}
