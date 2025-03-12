package ru.sberbank.sbp.sbp_transfer_service.entity;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * Сущность для хранения кода подтверждения
 * Содержит сам код и время его создания для контроля срока действия
 */
@Data
@Builder
public class ConfirmationCode {
    private String code;
    private String transferId;
    private LocalDateTime createdAt;
    private boolean used;
    
    /**
     * Проверка срока действия кода
     * По умолчанию код действителен 10 минут
     */
    public boolean isValid() {
        return !used && createdAt.plusMinutes(10).isAfter(LocalDateTime.now());
    }
}