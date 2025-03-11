package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.sbp_transfer_service.service.NotificationService;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.exception.NotificationException;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendConfirmationCode(String phoneNumber, String confirmationCode) {
        try {
            log.info("Sending confirmation code to {}", phoneNumber);
            // Simulate SMS sending
            log.info("Confirmation code {} sent to {}", confirmationCode, phoneNumber);
        } catch (Exception e) {
            throw new NotificationException("Failed to send confirmation code", e);
        }
    }

    @Override
    public void sendTransferSuccess(Transfer transfer) {
        try {
            log.info("Sending success notification for transfer {}", transfer.getId());
            // Simulate notification
        } catch (Exception e) {
            log.error("Failed to send success notification for transfer {}", transfer.getId(), e);
        }
    }

    @Override
    public void sendTransferFailure(Transfer transfer) {
        try {
            log.info("Sending failure notification for transfer {}", transfer.getId());
            // Simulate notification
        } catch (Exception e) {
            log.error("Failed to send failure notification for transfer {}", transfer.getId(), e);
        }
    }
}