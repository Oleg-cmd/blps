package ru.sberbank.sbp.lab2.transfer_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus;

@Entity
@Table(name = "transfers") // Укажем имя таблицы
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank
  @Pattern(regexp = "\\d{10}")
  @Column(nullable = false, length = 10)
  private String senderPhoneNumber;

  @NotBlank
  @Pattern(regexp = "\\d{10}")
  @Column(nullable = false, length = 10)
  private String recipientPhoneNumber;

  @NotNull
  @DecimalMin("0.01")
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @NotBlank
  @Column(nullable = false)
  private String recipientBankId;

  @Column(nullable = true) // Имя может быть не сразу известно
  private String recipientBankName;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransferStatus status;

  @NotNull
  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime completedAt;

  @Column(length = 6)
  private String confirmationCode;

  // ID транзакции в СБП (если есть)
  private String sbpTransactionId;

  @Column(nullable = false)
  private int retryCount = 0;

  private String failureReason;

  @Version // Для оптимистичной блокировки
  private Long version;
}
