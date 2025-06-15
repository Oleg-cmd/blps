package sbp.account.service;

import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.ReserveFundsCommand;

public interface AccountService {
  /**
   * Резервирует средства на счете отправителя.
   * Отправляет команду на отправку кода подтверждения.
   *
   * @param command Команда на резервирование средств.
   * @throws RuntimeException в случае ошибки.
   */
  void reserveFunds(ReserveFundsCommand command);

  /**
   * Обрабатывает списание/зачисление средств или отмену резерва.
   *
   * @param command Команда на списание/зачисление или отмену резерва.
   * @throws RuntimeException в случае ошибки.
   */
  void processFundsReleaseOrDebit(ReleaseFundsCommand command);
}
