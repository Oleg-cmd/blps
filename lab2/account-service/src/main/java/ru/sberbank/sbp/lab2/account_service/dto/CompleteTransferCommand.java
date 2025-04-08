package ru.sberbank.sbp.lab2.account_service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// Здесь не нужен Builder и общий интерфейс, так как этот класс только для приема
public class CompleteTransferCommand implements Serializable {

  private static final long serialVersionUID = 1L;

  private String senderPhoneNumber;
  private String recipientPhoneNumber;
  private BigDecimal amount;
  private UUID correlationId; // ID оригинального перевода (Transfer.id)
}
