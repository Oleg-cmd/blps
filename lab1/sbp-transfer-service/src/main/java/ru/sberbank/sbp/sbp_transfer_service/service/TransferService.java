package ru.sberbank.sbp.sbp_transfer_service.service;

import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.exception.InsufficientFundsException;
import ru.sberbank.sbp.sbp_transfer_service.exception.InvalidRecipientException;
import ru.sberbank.sbp.sbp_transfer_service.exception.TransferLimitExceededException;
import ru.sberbank.sbp.sbp_transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.sbp_transfer_service.dto.TransferConfirmationResponse;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

public interface TransferService {
    TransferInitiationResponse initiateTransfer(String senderPhoneNumber, String recipientPhoneNumber, 
        BigDecimal amount, String recipientBankId) throws InsufficientFundsException, 
        TransferLimitExceededException, InvalidRecipientException;
    
    TransferConfirmationResponse confirmTransfer(UUID transferId, String confirmationCode);
    
    Transfer getTransferStatus(UUID transferId);
    
    List<Transfer> getUserTransferHistory(String phoneNumber, int page, int size);
    
    BigDecimal getDailyTransferLimit(String phoneNumber);
    
    BigDecimal getCurrentDayTransferAmount(String phoneNumber);
}