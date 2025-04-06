package ru.sberbank.sbp.lab2.transfer_service.dto.jms;

import java.io.Serializable;
import java.util.UUID;

public interface AccountServiceCommand extends Serializable {
     UUID getCorrelationId();
}