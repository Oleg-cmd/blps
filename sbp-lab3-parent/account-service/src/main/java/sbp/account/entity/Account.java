package sbp.account.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Phone number is mandatory")
  @Pattern(regexp = "\\d{10}", message = "Phone number must be 10 digits")
  @Column(nullable = false, unique = true, length = 10)
  private String phoneNumber;

  @NotNull(message = "Balance cannot be null")
  @DecimalMin(value = "0.00", message = "Balance cannot be negative")
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal balance;

  @NotNull(message = "Reserved amount cannot be null")
  @DecimalMin(value = "0.00", message = "Reserved amount cannot be negative")
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal reservedAmount;

  @Version
  private Long version; // Для оптимистичной блокировки

  // Конструктор для удобного создания счета с начальными значениями
  public Account(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    this.balance = BigDecimal.ZERO;
    this.reservedAmount = BigDecimal.ZERO;
  }
}
