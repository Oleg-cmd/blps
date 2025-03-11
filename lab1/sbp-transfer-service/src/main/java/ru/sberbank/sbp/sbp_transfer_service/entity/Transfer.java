package ru.sberbank.sbp.sbp_transfer_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import ru.sberbank.sbp.sbp_transfer_service.entity.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Changed from transferId to id to match getter/setter naming convention

    @NotBlank
    @Pattern(regexp = "\\d{10}")
    @Column(nullable = false, length = 10)
    private String senderPhoneNumber;

    @NotBlank
    @Pattern(regexp = "\\d{10}")
    @Column(nullable = false, length = 10)
    private String recipientPhoneNumber;

    @DecimalMin("0.01")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String recipientBankId;

    @Column(nullable = false)
    private String recipientBankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @Column(length = 6)
    private String confirmationCode;

    private String sbpTransactionId;

    @Column(nullable = false)
    private int retryCount;

    private String failureReason;

    @Version
    private Long version;
}