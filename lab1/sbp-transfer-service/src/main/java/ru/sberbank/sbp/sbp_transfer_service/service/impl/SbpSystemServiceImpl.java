package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.sbp_transfer_service.service.SbpSystemService;
import ru.sberbank.sbp.sbp_transfer_service.entity.BankInfo;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.entity.FpsTransferResult;
import ru.sberbank.sbp.sbp_transfer_service.exception.BankNotFoundException;
import ru.sberbank.sbp.sbp_transfer_service.exception.SbpSystemException;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SbpSystemServiceImpl implements SbpSystemService {

    /**
     * Поиск банка получателя по номеру телефона
     * В реальной системе делает запрос к СБП
     */
    @Override
    public BankInfo findRecipientBank(String phoneNumber) {
        log.info("Looking up bank for phone number {}", phoneNumber);
        // In real implementation, this would call FPS API
        // Simulated response for now
        return BankInfo.builder()
                .bankId("100000000001")
                .bankName("СберБанк")
                .bankCode("044525225")
                .supportsSbp(true)
                .build();
    }

    @Override
    public boolean verifyBankParticipation(String bankId) {
        // In real implementation, this would verify with FPS
        return true;
    }

    /**
     * Обработка перевода через СБП
     * Симуляция взаимодействия с внешней системой
     */
    @Override
    public String processTransfer(Transfer transfer) {
        log.info("Processing transfer {} through FPS", transfer.getId());
        // Simulate FPS processing
        String sbpTransactionId = "SBP" + System.currentTimeMillis();
        
        // In real implementation, this would make API call to FPS
        if (Math.random() < 0.95) { // 95% success rate simulation
            return sbpTransactionId;
        }
        
        throw new SbpSystemException("FPS transfer processing failed");
    }

    /**
     * Ожидание подтверждения от СБП
     * Симуляция асинхронного ответа от внешней системы
     */
    @Override
    public FpsTransferResult waitForFpsConfirmation(String sbpTransactionId) {
        log.info("Waiting for FPS confirmation for transaction {}", sbpTransactionId);
        // Simulate waiting for FPS confirmation
        try {
            Thread.sleep(1000); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return FpsTransferResult.builder()
                .successful(true)
                .transactionId(sbpTransactionId)
                .processedAt(LocalDateTime.now())
                .build();
    }
}