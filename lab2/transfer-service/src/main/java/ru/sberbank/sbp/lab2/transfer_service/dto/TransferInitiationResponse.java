package ru.sberbank.sbp.lab2.transfer_service.dto;

import java.util.UUID;
import lombok.Value;
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus;

@Value // Неизменяемый DTO, только геттеры, equals, hashCode, toString, конструктор со всеми полями
public class TransferInitiationResponse {

  UUID transferId;
  TransferStatus status; // Статус после инициации (обычно AWAITING_CONFIRMATION)
  String recipientBankName; // Имя банка получателя (если известно)
}
