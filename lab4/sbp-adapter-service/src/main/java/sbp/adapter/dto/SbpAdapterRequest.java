package sbp.adapter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SbpAdapterRequest {

  @NotBlank(message = "Sender phone number is mandatory")
  @Pattern(
    regexp = "\\d{10}",
    message = "Sender phone number must be 10 digits"
  )
  private String senderPhoneNumber;

  @NotBlank(message = "Recipient phone number is mandatory")
  @Pattern(
    regexp = "\\d{10}",
    message = "Recipient phone number must be 10 digits"
  )
  private String recipientPhoneNumber;

  @NotNull(message = "Amount is mandatory")
  @DecimalMin(value = "0.01", message = "Amount must be positive")
  private BigDecimal amount;

  @NotNull(message = "Correlation ID is mandatory")
  private UUID correlationId;
}
