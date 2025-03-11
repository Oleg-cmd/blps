package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.entity.BankInfo;
import ru.sberbank.sbp.sbp_transfer_service.entity.FpsTransferResult;
import ru.sberbank.sbp.sbp_transfer_service.entity.enums.TransferStatus;
import ru.sberbank.sbp.sbp_transfer_service.repository.TransferRepository;
import ru.sberbank.sbp.sbp_transfer_service.service.*;
import ru.sberbank.sbp.sbp_transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.sbp_transfer_service.dto.TransferConfirmationResponse;
import ru.sberbank.sbp.sbp_transfer_service.exception.*;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final TransferRepository transferRepository;
    private final SbpSystemService sbpSystemService;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final PhoneValidationService phoneValidationService;
    
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal("150000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Override
    @Transactional
    public TransferInitiationResponse initiateTransfer(String senderPhoneNumber, 
            String recipientPhoneNumber, BigDecimal amount, String recipientBankId) {
        log.info("Initiating transfer from {} to {}", senderPhoneNumber, recipientPhoneNumber);
        
        // Step 1: Phone number validation (according to BPMN)
        if (!phoneValidationService.validatePhoneFormat(recipientPhoneNumber)) {
            throw new PhoneFormatException("Invalid phone number format");
        }

        // Step 2: Bank verification in FPS
        BankInfo recipientBank;
        try {
            recipientBank = sbpSystemService.findRecipientBank(recipientPhoneNumber);
        } catch (BankNotFoundException e) {
            throw new InvalidRecipientException("Recipient's bank not found in FPS");
        }

        // Step 3: Balance check
        validateBalanceAndLimits(senderPhoneNumber, amount);

        // Step 4: Create pending transfer
        Transfer transfer = createPendingTransfer(senderPhoneNumber, recipientPhoneNumber, 
            amount, recipientBank);

        // Step 5: Reserve funds
        try {
            accountService.reserveFunds(senderPhoneNumber, amount);
        } catch (Exception e) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason("Failed to reserve funds");
            transferRepository.save(transfer);
            throw new TransferProcessingException("Failed to reserve funds", e);
        }

        // Step 6: Send confirmation code
        try {
            notificationService.sendConfirmationCode(senderPhoneNumber, transfer.getConfirmationCode());
        } catch (NotificationException e) {
            accountService.releaseReservedFunds(senderPhoneNumber, amount);
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason("Failed to send confirmation code");
            transferRepository.save(transfer);
            throw new TransferProcessingException("Failed to send confirmation code", e);
        }

        return new TransferInitiationResponse(
            transfer.getId(),
            transfer.getStatus(),
            transfer.getRecipientBankName()
        );
    }

    @Override
    @Transactional
    public TransferConfirmationResponse confirmTransfer(UUID transferId, String confirmationCode) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));

        // Step 1: Validate transfer state
        validateTransferState(transfer);

        // Step 2: Verify confirmation code
        if (!verifyConfirmationCode(transfer, confirmationCode)) {
            return handleInvalidCode(transfer);
        }

        // Step 3: Process via FPS
        try {
            // Request to FPS
            String sbpTransactionId = sbpSystemService.processTransfer(transfer);
            
            // Wait for FPS confirmation
            FpsTransferResult fpsResult = sbpSystemService.waitForFpsConfirmation(sbpTransactionId);
            
            if (fpsResult.isSuccessful()) {
                return completeSuccessfulTransfer(transfer, sbpTransactionId);
            } else {
                return handleFailedTransfer(transfer, fpsResult.getErrorMessage());
            }
        } catch (SbpSystemException e) {
            return handleSystemError(transfer, e);
        }
    }

    private void validateBalanceAndLimits(String phoneNumber, BigDecimal amount) {
        BigDecimal dailyTotal = getCurrentDayTransferAmount(phoneNumber);
        if (dailyTotal.add(amount).compareTo(DAILY_TRANSFER_LIMIT) > 0) {
            throw new TransferLimitExceededException("Daily transfer limit exceeded");
        }

        if (!accountService.hasEnoughBalance(phoneNumber, amount)) {
            throw new InsufficientFundsException("Insufficient funds for transfer");
        }
    }

    private Transfer createPendingTransfer(String senderPhoneNumber, String recipientPhoneNumber, 
            BigDecimal amount, BankInfo recipientBank) {
        Transfer transfer = Transfer.builder()
                .senderPhoneNumber(senderPhoneNumber)
                .recipientPhoneNumber(recipientPhoneNumber)
                .amount(amount)
                .recipientBankId(recipientBank.getBankId())
                .recipientBankName(recipientBank.getBankName())
                .status(TransferStatus.AWAITING_CONFIRMATION)
                .confirmationCode(generateSecureConfirmationCode())
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();

        return transferRepository.save(transfer);
    }

    @Override
    @Cacheable(value = "transferHistory", key = "#phoneNumber + '_' + #page + '_' + #size")
    public List<Transfer> getUserTransferHistory(String phoneNumber, int page, int size) {
        return transferRepository.findBySenderPhoneNumberOrderByCreatedAtDesc(
            phoneNumber, PageRequest.of(page, size));
    }

    @Override
    public BigDecimal getCurrentDayTransferAmount(String phoneNumber) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0)
            .withSecond(0).withNano(0);
        return transferRepository.sumTransferAmountsByPhoneNumberAndDate(phoneNumber, startOfDay);
    }

    @Override
    public BigDecimal getDailyTransferLimit(String phoneNumber) {
        return DAILY_TRANSFER_LIMIT;
    }

    private String generateSecureConfirmationCode() {
        return String.format("%06d", new SecureRandom().nextInt(1000000));
    }

    private void validateTransferState(Transfer transfer) {
        if (transfer.getStatus() != TransferStatus.AWAITING_CONFIRMATION) {
            throw new InvalidTransferStateException("Transfer is not awaiting confirmation");
        }
        if (transfer.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now())) {
            throw new TransferExpiredException("Transfer confirmation timeout");
        }
    }

    @Override
    public Transfer getTransferStatus(UUID transferId) {
        return transferRepository.findById(transferId)
            .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));
    }

    private boolean verifyConfirmationCode(Transfer transfer, String confirmationCode) {
        return transfer.getConfirmationCode().equals(confirmationCode);
    }

    private TransferConfirmationResponse handleInvalidCode(Transfer transfer) {
        transfer.setRetryCount(transfer.getRetryCount() + 1);
        
        if (transfer.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason("Maximum retry attempts exceeded");
            transferRepository.save(transfer);
            accountService.releaseReservedFunds(transfer.getSenderPhoneNumber(), transfer.getAmount());
            return new TransferConfirmationResponse(transfer.getId(), TransferStatus.FAILED, 
                "Maximum retry attempts exceeded");
        }
        
        transferRepository.save(transfer);
        return new TransferConfirmationResponse(transfer.getId(), transfer.getStatus(), 
            "Invalid confirmation code");
    }

    private TransferConfirmationResponse completeSuccessfulTransfer(Transfer transfer, 
            String sbpTransactionId) {
        transfer.setStatus(TransferStatus.SUCCESSFUL);
        transfer.setSbpTransactionId(sbpTransactionId);
        transfer.setCompletedAt(LocalDateTime.now());
        transferRepository.save(transfer);
        
        accountService.completeFundsTransfer(transfer.getSenderPhoneNumber(), 
            transfer.getRecipientPhoneNumber(), transfer.getAmount());
        notificationService.sendTransferSuccess(transfer);
        
        return new TransferConfirmationResponse(transfer.getId(), TransferStatus.SUCCESSFUL, null);
    }

    private TransferConfirmationResponse handleFailedTransfer(Transfer transfer, String errorMessage) {
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason(errorMessage);
        transferRepository.save(transfer);
        
        accountService.releaseReservedFunds(transfer.getSenderPhoneNumber(), transfer.getAmount());
        notificationService.sendTransferFailure(transfer);
        
        return new TransferConfirmationResponse(transfer.getId(), TransferStatus.FAILED, errorMessage);
    }

    private TransferConfirmationResponse handleSystemError(Transfer transfer, Exception e) {
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason("System error: " + e.getMessage());
        transferRepository.save(transfer);
        
        accountService.releaseReservedFunds(transfer.getSenderPhoneNumber(), transfer.getAmount());
        notificationService.sendTransferFailure(transfer);
        
        return new TransferConfirmationResponse(transfer.getId(), TransferStatus.FAILED, 
            "System error occurred");
    }
}





