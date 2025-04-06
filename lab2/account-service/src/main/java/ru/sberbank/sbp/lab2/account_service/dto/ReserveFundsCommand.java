package ru.sberbank.sbp.lab2.account_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID; // Для идентификатора корреляции

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveFundsCommand implements Serializable { // Должен быть Serializable

    private static final long serialVersionUID = 1L; // Хорошая практика для Serializable

    private String phoneNumber;
    private BigDecimal amount;
    private UUID correlationId; // ID для связи запроса и ответа, если понадобится
                                // Или для отслеживания операции в логах
}