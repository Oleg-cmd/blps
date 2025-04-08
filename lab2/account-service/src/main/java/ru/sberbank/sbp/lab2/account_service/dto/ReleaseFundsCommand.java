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
public class ReleaseFundsCommand implements Serializable {

  private static final long serialVersionUID = 1L;
  private String phoneNumber;
  private BigDecimal amount;
  private UUID correlationId;
}
