package ru.sberbank.sbp.sbp_transfer_service.service;

import ru.sberbank.sbp.sbp_transfer_service.entity.BankInfo;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.entity.FpsTransferResult;

public interface SbpSystemService {
    BankInfo findRecipientBank(String phoneNumber);
    boolean verifyBankParticipation(String bankId);
    String processTransfer(Transfer transfer);
    FpsTransferResult waitForFpsConfirmation(String sbpTransactionId);
}