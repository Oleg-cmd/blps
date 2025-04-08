package ru.sberbank.sbp.lab2.transfer_service.dto;

import java.util.UUID;
import lombok.Value;
import ru.sberbank.sbp.lab2.transfer_service.entity.enums.TransferStatus; // Импорт статуса

@Value
public class TransferConfirmationResponse {

  UUID transferId;
  TransferStatus status; // Финальный статус (SUCCESSFUL, FAILED, ...)
  String message; // Сообщение об ошибке или успехе
}
