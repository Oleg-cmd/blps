package ru.sberbank.sbp.lab2.transfer_service.dto;

import lombok.Value; // Делает класс неизменяемым (immutable)
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus; // Импорт статуса
import java.util.UUID;

@Value // Неизменяемый DTO, только геттеры, equals, hashCode, toString, конструктор со всеми полями
public class TransferInitiationResponse {
    UUID transferId;
    TransferStatus status; // Статус после инициации (обычно AWAITING_CONFIRMATION)
    String recipientBankName; // Имя банка получателя (если известно)
}