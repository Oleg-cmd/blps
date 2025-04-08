package ru.sberbank.sbp.lab2.notification_service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendSuccessNotificationCommand implements Serializable {

  private static final long serialVersionUID = 1L;
  private String senderPhoneNumber;
  private BigDecimal amount;
  private String recipientInfo;
  private UUID correlationId;
}
