package ru.sberbank.sbp.lab2.transfer_service.dto.jms;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendConfirmationCodeCommand implements NotificationCommand {

  private static final long serialVersionUID = 1L;
  private String phoneNumber;
  private String code;
  private UUID correlationId;
}
