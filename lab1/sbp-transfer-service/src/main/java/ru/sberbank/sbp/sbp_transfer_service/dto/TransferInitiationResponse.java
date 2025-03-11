package ru.sberbank.sbp.sbp_transfer_service.dto;

import lombok.Value;
import ru.sberbank.sbp.sbp_transfer_service.entity.enums.TransferStatus;
import java.util.UUID;

@Value
public class TransferInitiationResponse {
    UUID transferId;
    TransferStatus status;
    String recipientBankName;
}