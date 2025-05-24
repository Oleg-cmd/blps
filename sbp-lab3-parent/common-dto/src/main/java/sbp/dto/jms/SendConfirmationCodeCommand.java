package sbp.dto.jms;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendConfirmationCodeCommand implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID correlationId; // ID оригинального перевода (Transfer.id)
  private String phoneNumber; // Номер телефона, на который нужно "отправить" код
  private String code; // Сам код подтверждения
}
