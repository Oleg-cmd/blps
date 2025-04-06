package ru.sberbank.sbp.lab2.account_service.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import ru.sberbank.sbp.lab2.account_service.dto.ReserveFundsCommand;
import ru.sberbank.sbp.lab2.account_service.exception.AccountNotFoundException;
import ru.sberbank.sbp.lab2.account_service.exception.InsufficientFundsException;
import ru.sberbank.sbp.lab2.account_service.service.AccountService;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCommandListener {

  private final AccountService accountService;

  @JmsListener(
    destination = JmsConfig.ACCOUNT_COMMAND_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleAccountCommand(@Payload ReserveFundsCommand command) {
    log.info(
      "Received and converted JMS message on queue '{}': {}",
      JmsConfig.ACCOUNT_COMMAND_QUEUE,
      command
    );

    try {
      accountService.reserveFunds(
        command.getPhoneNumber(),
        command.getAmount()
      );
      log.info(
        "Successfully processed ReserveFundsCommand with correlationId: {}",
        command.getCorrelationId()
      );
    } catch (AccountNotFoundException | InsufficientFundsException e) {
      // Логируем ожидаемые бизнес-ошибки как WARN или INFO
      log.warn(
        "Business error processing ReserveFundsCommand with correlationId: {}. Reason: {}",
        command.getCorrelationId(),
        e.getMessage()
      );
      // Перевыбрасываем как RuntimeException, чтобы JTA откатила транзакцию
      throw new RuntimeException(
        "Business error processing command " + command.getCorrelationId(),
        e
      );
    } catch (Exception e) {
      // Логируем неожиданные ошибки как ERROR
      log.error(
        "Unexpected error processing ReserveFundsCommand with correlationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      // Перевыбрасываем как RuntimeException, чтобы JTA откатила транзакцию
      throw new RuntimeException(
        "Unexpected error processing command " + command.getCorrelationId(),
        e
      );
    }
  }
}
