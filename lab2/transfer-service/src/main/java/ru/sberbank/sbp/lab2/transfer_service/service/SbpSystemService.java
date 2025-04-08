package ru.sberbank.sbp.lab2.transfer_service.service;

import java.util.Optional;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;
import ru.sberbank.sbp.lab2.transfer_service.integration.models.BankInfo;
import ru.sberbank.sbp.lab2.transfer_service.integration.models.SbpTransferResponse;

public interface SbpSystemService {
  /**
   * Находит банк получателя по номеру телефона через SBP Adapter.
   * @param phoneNumber Номер телефона получателя.
   * @return Optional с информацией о банке, если найден, иначе Optional.empty().
   */
  Optional<BankInfo> findRecipientBank(String phoneNumber);

  /**
   * Инициирует обработку перевода через SBP Adapter.
   * @param transfer Детали перевода.
   * @return Ответ от SBP Adapter с результатом операции.
   */
  SbpTransferResponse processTransferViaSbp(Transfer transfer);
}
