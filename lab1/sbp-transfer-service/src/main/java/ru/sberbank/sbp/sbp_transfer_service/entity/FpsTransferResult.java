package ru.sberbank.sbp.sbp_transfer_service.entity;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FpsTransferResult {
    private boolean successful;
    private String transactionId;
    private String errorMessage;
    private String recipientBank;
    private LocalDateTime processedAt;
}
