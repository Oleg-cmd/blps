package ru.sberbank.sbp.lab2.transfer_service.dto.jms;

import java.io.Serializable;
import java.util.UUID;

// Опциональный общий интерфейс для команд уведомлений
public interface NotificationCommand extends Serializable {
  UUID getCorrelationId(); // ID исходного перевода
}
