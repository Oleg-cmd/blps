package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.sbp_transfer_service.service.PhoneValidationService;
import ru.sberbank.sbp.sbp_transfer_service.exception.PhoneFormatException;

@Service
@Slf4j
public class PhoneValidationServiceImpl implements PhoneValidationService {

    /**
     * Валидация номера телефона
     * Проверяет:
     * 1. Формат номера телефона
     * 2. Соответствие российскому формату
     * 3. Допустимость символов
     */
    @Override
    public boolean validatePhoneFormat(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            log.warn("Phone number is null or empty");
            return false;
        }
        
        // Проверяем что номер состоит только из 10 цифр (формат без кода страны)
        boolean isValid = phoneNumber.matches("\\d{10}");
        if (!isValid) {
            log.warn("Invalid phone number format: {}", phoneNumber);
        }
        
        return isValid;
    }

    /**
     * Форматирование номера телефона
     * Приводит номер к единому формату:
     * 1. Удаляет лишние символы
     * 2. Добавляет код страны если необходимо
     * 3. Проверяет корректность формата
     */
    @Override
    public String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            throw new PhoneFormatException("Phone number cannot be null");
        }

        // Удаляем все нецифровые символы
        String cleaned = phoneNumber.replaceAll("[^\\d]", "");
        
        // Обрабатываем различные форматы
        if (cleaned.length() == 11) {
            if (cleaned.startsWith("7") || cleaned.startsWith("8")) {
                cleaned = cleaned.substring(1);
            } else {
                throw new PhoneFormatException("Invalid country code");
            }
        } else if (cleaned.length() == 10) {
            // Номер уже в правильном формате
        } else {
            throw new PhoneFormatException("Invalid phone number length");
        }

        log.debug("Formatted phone number {} to {}", phoneNumber, cleaned);
        return cleaned;
    }
}
