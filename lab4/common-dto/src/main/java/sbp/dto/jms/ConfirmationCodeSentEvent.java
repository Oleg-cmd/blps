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
public class ConfirmationCodeSentEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID correlationId; // ID оригинального перевода (Transfer.id)
}
