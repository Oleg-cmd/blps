package ru.sberbank.sbp.lab2.notification_service.dto;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendConfirmationCodeCommand implements Serializable {

  private static final long serialVersionUID = 1L;
  private String phoneNumber;
  private String code;
  private UUID correlationId;
}
