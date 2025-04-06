package ru.sberbank.sbp.lab2.transfer_service.dto.jms;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveFundsCommand implements AccountServiceCommand {

  private static final long serialVersionUID = 1L;

  private String phoneNumber;
  private BigDecimal amount;
  private UUID correlationId; // ID перевода Transfer.id
}
