package ru.sberbank.sbp.lab2.account_service.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.sberbank.sbp.lab2.account_service.dto.NotificationCommand;

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
