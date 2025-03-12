package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.sbp_transfer_service.service.NotificationService;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.entity.ConfirmationCode;
import ru.sberbank.sbp.sbp_transfer_service.exception.NotificationException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    /**
     * Временное хранилище кодов подтверждения
     * В реальной системе должно быть заменено на защищенное хранилище
     * с механизмом истечения срока действия кодов
     */
    private final Map<String, ConfirmationCode> confirmationCodes = new ConcurrentHashMap<>();

    // Задержка для имитации асинхронности (в миллисекундах)
    private static final long SIMULATED_DELAY = 1000; // 1 секунда

    /**
     * Отправка кода подтверждения
     * В реальной системе:
     * 1. Генерация случайного защищенного кода
     * 2. Отправка через SMS или Push-уведомление
     * 3. Сохранение кода в защищенном хранилище
     */
    @Override
    public void sendConfirmationCode(String phoneNumber, String transferId) {
        log.info("Start sending confirmation code to {}", phoneNumber);
        simulateDelay(); // Имитация задержки
        try {
            log.info("Sending confirmation code to {}", phoneNumber);
            // Simulate SMS sending
            log.info("Confirmation code {} sent to {}", transferId, phoneNumber);
        } catch (Exception e) {
            throw new NotificationException("Failed to send confirmation code", (Exception) e); // Чуть более явно указан тип приведения
        }
        log.info("Finished sending confirmation code to {}", phoneNumber);
    }

    /**
     * Проверка кода подтверждения
     * Верифицирует введенный пользователем код
     * В случае неверного кода выбрасывает исключение
     */
    @Override
    public void verifyConfirmationCode(String transferId, String code) {
        log.info("Start verifying confirmation code for transfer {}", transferId);
        simulateDelay(); // Имитация задержки
        // Simulate verification
        if (!confirmationCodes.containsKey(transferId) || !confirmationCodes.get(transferId).getCode().equals(code)) {
            throw new NotificationException("Invalid confirmation code", null); // Исправленный конструктор
        }
        log.info("Finished verifying confirmation code for transfer {}", transferId);
    }

    @Override
    public void sendTransferSuccess(Transfer transfer) {
        log.info("Start sending success notification for transfer {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        try {
            log.info("Sending success notification for transfer {}", transfer.getId());
            // Simulate notification
        } catch (Exception e) {
            log.error("Failed to send success notification for transfer {}", transfer.getId(), e);
        }
        log.info("Finished sending success notification for transfer {}", transfer.getId());
    }

    @Override
    public void sendTransferFailure(Transfer transfer) {
        log.info("Start sending failure notification for transfer {}", transfer.getId());
        simulateDelay(); // Имитация задержки
        try {
            log.info("Sending failure notification for transfer {}", transfer.getId());
            // Simulate notification
        } catch (Exception e) {
            log.error("Failed to send failure notification for transfer {}", transfer.getId(), e);
        }
        log.info("Finished sending failure notification for transfer {}", transfer.getId());
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