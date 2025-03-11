package ru.sberbank.sbp.sbp_transfer_service.dto;

import lombok.Value;
import ru.sberbank.sbp.sbp_transfer_service.entity.enums.TransferStatus;
import java.util.UUID;

@Value
public class TransferConfirmationResponse {
    UUID transferId;
    TransferStatus status;
    String message;
}