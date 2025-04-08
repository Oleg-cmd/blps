package ru.sberbank.sbp.lab2.account_service.dto;

import java.io.Serializable;
import java.util.UUID;

// Опциональный общий интерфейс для команд уведомлений
public interface NotificationCommand extends Serializable {
  UUID getCorrelationId(); // ID исходного перевода
}
