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
    // Основные зависимости сервиса
    private final TransferRepository transferRepository;
    private final SbpSystemService sbpSystemService;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final PhoneValidationService phoneValidationService;

    // Константы для бизнес-логики
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal("150000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    // Задержка для имитации асинхронности (в миллисекундах)
    private static final long SIMULATED_DELAY = 1000; // 1 секунда

    /**
     * Инициация нового перевода
     * Процесс включает:
     * 1. Валидацию номера телефона
     * 2. Проверку банка получателя
     * 3. Проверку баланса и лимитов
     * 4. Создание перевода
     * 5. Резервирование средств
     * 6. Отправку кода подтверждения
     */
    @Override
    @Transactional
    public TransferInitiationResponse initiateTransfer(String senderPhoneNumber,
            String recipientPhoneNumber, BigDecimal amount, String recipientBankId) {
        log.info("Start initiating transfer from {} to {}", senderPhoneNumber, recipientPhoneNumber);
        simulateDelay(); // Имитация задержки

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

        log.info("Finished initiating transfer from {} to {}", senderPhoneNumber, recipientPhoneNumber);
        return new TransferInitiationResponse(
            transfer.getId(),
            transfer.getStatus(),
            transfer.getRecipientBankName()
        );
    }

    /**
     * Подтверждение перевода по коду
     * Включает:
     * 1. Проверку статуса перевода
     * 2. Верификацию кода
     * 3. Обработку через СБП
     * 4. Завершение перевода
     */
    @Override
    @Transactional
    public TransferConfirmationResponse confirmTransfer(UUID transferId, String confirmationCode) {
        log.info("Start confirming transfer for transferId {}", transferId);
        simulateDelay(); // Имитация задержки

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
        finally {
            log.info("Finished confirming transfer for transferId {}", transferId);
        }
    }

    /**
     * Проверка баланса и дневных лимитов
     */
    private void validateBalanceAndLimits(String phoneNumber, BigDecimal amount) {
        log.info("Start validating balance and limits for phone number {}", phoneNumber);
        simulateDelay(); // Имитация задержки
        BigDecimal dailyTotal = getCurrentDayTransferAmount(phoneNumber);
        if (dailyTotal.add(amount).compareTo(DAILY_TRANSFER_LIMIT) > 0) {
            throw new TransferLimitExceededException("Daily transfer limit exceeded");
        }

        if (!accountService.hasEnoughBalance(phoneNumber, amount)) {
            throw new InsufficientFundsException("Insufficient funds for transfer");
        }
        log.info("Finished validating balance and limits for phone number {}", phoneNumber);
    }

    /**
     * Создание нового перевода в статусе ожидания подтверждения
     */
    private Transfer createPendingTransfer(String senderPhoneNumber, String recipientPhoneNumber,
            BigDecimal amount, BankInfo recipientBank) {
        log.info("Start creating pending transfer for phone number {}", senderPhoneNumber);
        simulateDelay(); // Имитация задержки
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

        Transfer savedTransfer = transferRepository.save(transfer);
        log.info("Finished creating pending transfer for phone number {}", senderPhoneNumber);
        return savedTransfer;
    }

    /**
     * Получение истории переводов пользователя с кэшированием
     */
    @Override
    @Cacheable(value = "transferHistory", key = "#phoneNumber + '_' + #page + '_' + #size")
    public List<Transfer> getUserTransferHistory(String phoneNumber, int page, int size) {
        log.info("Start getting user transfer history for phone number {}", phoneNumber);
        simulateDelay(); // Имитация задержки
        List<Transfer> history = transferRepository.findBySenderPhoneNumberOrderByCreatedAtDesc(
            phoneNumber, PageRequest.of(page, size));
        log.info("Finished getting user transfer history for phone number {}", phoneNumber);
        return history;
    }

    @Override
    public BigDecimal getCurrentDayTransferAmount(String phoneNumber) {
        log.info("Start getting current day transfer amount for phone number {}", phoneNumber);
        simulateDelay(); // Имитация задержки
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0)
            .withSecond(0).withNano(0);
        BigDecimal amount = transferRepository.sumTransferAmountsByPhoneNumberAndDate(phoneNumber, startOfDay);
        log.info("Finished getting current day transfer amount for phone number {}", phoneNumber);
        return amount;
    }

    @Override
    public BigDecimal getDailyTransferLimit(String phoneNumber) {
        log.info("Start getting daily transfer limit for phone number {}", phoneNumber);
        simulateDelay(); // Имитация задержки
        log.info("Finished getting daily transfer limit for phone number {}", phoneNumber);
        return DAILY_TRANSFER_LIMIT;
    }

    private String generateSecureConfirmationCode() {
        log.info("Start generating secure confirmation code");
        simulateDelay(); // Имитация задержки
        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        log.info("Finished generating secure confirmation code");
        return code;
    }

    private void validateTransferState(Transfer transfer) {
        log.info("Start validating transfer state for transferId {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        if (transfer.getStatus() != TransferStatus.AWAITING_CONFIRMATION) {
            throw new InvalidTransferStateException("Transfer is not awaiting confirmation");
        }
        if (transfer.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now())) {
            throw new TransferExpiredException("Transfer confirmation timeout");
        }
        log.info("Finished validating transfer state for transferId {}", transfer.getId());
    }

    @Override
    public Transfer getTransferStatus(UUID transferId) {
        log.info("Start getting transfer status for transferId {}", transferId);
        simulateDelay(); // Имитация задержки
        log.info("TransferServiceImpl.getTransferStatus: Attempting to retrieve transfer with ID: {}", transferId); // Логирование входа в метод
        try {
            Transfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> {
                        log.warn("TransferServiceImpl.getTransferStatus: Transfer not found with ID: {}", transferId); // Логирование TransferNotFoundException
                        return new TransferNotFoundException("Transfer not found");
                    });
            log.info("TransferServiceImpl.getTransferStatus: Successfully retrieved transfer with ID: {}", transferId); // Логирование успешного получения
            return transfer;
        } catch (TransferNotFoundException e) {
            throw e; // Перебрасываем исключение, чтобы контроллер мог обработать (хотя orElseThrow уже должен это делать)
        } catch (Exception e) {
            log.error("TransferServiceImpl.getTransferStatus: Unexpected error retrieving transfer with ID: {}", transferId, e); // Логирование неожиданной ошибки
            throw new TransferProcessingException("Error retrieving transfer status", e); // Бросаем TransferProcessingException
        } finally {
            log.info("Finished getting transfer status for transferId {}", transferId);
        }
    }

    private boolean verifyConfirmationCode(Transfer transfer, String confirmationCode) {
        log.info("Start verifying confirmation code for transferId {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        boolean verified = transfer.getConfirmationCode().equals(confirmationCode);
        log.info("Finished verifying confirmation code for transferId {}, code verified: {}", transfer.getId(), verified);
        return verified;
    }

    private TransferConfirmationResponse handleInvalidCode(Transfer transfer) {
        log.info("Start handling invalid code for transferId {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        transfer.setRetryCount(transfer.getRetryCount() + 1);

        if (transfer.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason("Maximum retry attempts exceeded");
            transferRepository.save(transfer);
            accountService.releaseReservedFunds(transfer.getSenderPhoneNumber(), transfer.getAmount());
            log.info("Finished handling invalid code for transferId {} - Max retries exceeded", transfer.getId());
            return new TransferConfirmationResponse(transfer.getId(), TransferStatus.FAILED,
                "Maximum retry attempts exceeded");
        }

        transferRepository.save(transfer);
        log.info("Finished handling invalid code for transferId {} - Invalid code, retries remaining", transfer.getId());
        return new TransferConfirmationResponse(transfer.getId(), transfer.getStatus(),
            "Invalid confirmation code");
    }

    private TransferConfirmationResponse completeSuccessfulTransfer(Transfer transfer,
            String sbpTransactionId) {
        log.info("Start completing successful transfer for transferId {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        transfer.setStatus(TransferStatus.SUCCESSFUL);
        transfer.setSbpTransactionId(sbpTransactionId);
        transfer.setCompletedAt(LocalDateTime.now());
        transferRepository.save(transfer);

        accountService.completeFundsTransfer(transfer.getSenderPhoneNumber(),
            transfer.getRecipientPhoneNumber(), transfer.getAmount());
        notificationService.sendTransferSuccess(transfer);

        log.info("Finished completing successful transfer for transferId {}", transfer.getId());
        return new TransferConfirmationResponse(transfer.getId(), TransferStatus.SUCCESSFUL, null);
    }

    private TransferConfirmationResponse handleFailedTransfer(Transfer transfer, String errorMessage) {
        log.info("Start handling failed transfer for transferId {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason(errorMessage);
        transferRepository.save(transfer);

        accountService.releaseReservedFunds(transfer.getSenderPhoneNumber(), transfer.getAmount());
        notificationService.sendTransferFailure(transfer);

        log.info("Finished handling failed transfer for transferId {}", transfer.getId());
        return new TransferConfirmationResponse(transfer.getId(), TransferStatus.FAILED, errorMessage);
    }

    private TransferConfirmationResponse handleSystemError(Transfer transfer, Exception e) {
        log.info("Start handling system error for transferId {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason("System error: " + e.getMessage());
        transferRepository.save(transfer);

        accountService.releaseReservedFunds(transfer.getSenderPhoneNumber(), transfer.getAmount());
        notificationService.sendTransferFailure(transfer);

        log.info("Finished handling system error for transferId {}", transfer.getId());
        return new TransferConfirmationResponse(transfer.getId(), TransferStatus.FAILED,
            "System error occurred");
    }

    private void simulateDelay() {
        try {
            Thread.sleep(SIMULATED_DELAY);
        } catch (InterruptedException e) {
            log.warn("Simulated delay interrupted", e);
            Thread.currentThread().interrupt(); // Re-interrupt the thread to respect interruption policy
        }
    }
}