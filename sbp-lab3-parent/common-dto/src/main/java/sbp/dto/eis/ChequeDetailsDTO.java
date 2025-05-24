package sbp.dto.eis;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChequeDetailsDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  @NotNull(message = "Transaction ID is mandatory for cheque")
  private UUID transactionId; // ID оригинального перевода (Transfer.id)

  @NotBlank(message = "Recipient email is mandatory for cheque")
  @Email(message = "Invalid email format for recipient")
  private String recipientEmail;

  @NotBlank(message = "Sender information is mandatory")
  private String senderInfo; // Например, номер телефона или имя отправителя

  @NotBlank(message = "Recipient information is mandatory")
  private String recipientInfo; // Например, номер телефона или имя получателя

  @NotNull(message = "Amount is mandatory")
  @DecimalMin(value = "0.01", message = "Amount must be positive")
  private BigDecimal amount;

  @NotNull(message = "Transaction timestamp is mandatory")
  private LocalDateTime transactionTimestamp;

  @NotBlank(message = "Cheque subject is mandatory")
  private String subject; // Тема письма с чеком

  private String operationDetails; // Например, "Перевод средств по СБП"
}
