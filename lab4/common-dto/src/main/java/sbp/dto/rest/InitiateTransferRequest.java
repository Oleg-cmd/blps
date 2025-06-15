package sbp.dto.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiateTransferRequest {

  @NotBlank(message = "Recipient phone number is mandatory")
  @Pattern(
    regexp = "\\d{10}",
    message = "Recipient phone number must be 10 digits"
  )
  private String recipientPhoneNumber;

  @NotBlank(message = "Recipient bank ID is mandatory")
  private String bankId; // BIC or other identifier of the recipient's bank

  @NotNull(message = "Transfer amount is mandatory")
  @DecimalMin(
    value = "0.01",
    message = "Transfer amount must be greater than 0"
  )
  private BigDecimal amount;
}
