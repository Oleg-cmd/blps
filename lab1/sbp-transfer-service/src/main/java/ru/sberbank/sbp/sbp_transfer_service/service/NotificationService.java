package ru.sberbank.sbp.sbp_transfer_service.service;

import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;

public interface NotificationService {
    void sendConfirmationCode(String phoneNumber, String transferId);
    void verifyConfirmationCode(String transferId, String code);
    void sendTransferSuccess(Transfer transfer);
    void sendTransferFailure(Transfer transfer);
}