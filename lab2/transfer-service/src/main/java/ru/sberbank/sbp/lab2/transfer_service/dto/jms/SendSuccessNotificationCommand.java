package ru.sberbank.sbp.lab2.transfer_service.dto.jms;

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
public class SendSuccessNotificationCommand implements NotificationCommand {

  private static final long serialVersionUID = 1L;
  private String senderPhoneNumber; // кому отправлять
  private BigDecimal amount;
  private String recipientInfo; // Инфо о получателе (номер/банк)
  private UUID correlationId;
}
