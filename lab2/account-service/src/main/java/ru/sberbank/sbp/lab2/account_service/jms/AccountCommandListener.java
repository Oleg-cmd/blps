package ru.sberbank.sbp.lab2.account_service.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import ru.sberbank.sbp.lab2.account_service.dto.CompleteTransferCommand;
import ru.sberbank.sbp.lab2.account_service.dto.ReleaseFundsCommand;
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
    destination = JmsConfig.ACCOUNT_RESERVE_FUNDS_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleReserveFundsCommand(@Payload ReserveFundsCommand command) {
    log.info(
      "Received and converted JMS message on queue '{}': {}",
      JmsConfig.ACCOUNT_RESERVE_FUNDS_QUEUE,
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
      log.warn(
        "Business error processing ReserveFundsCommand with correlationId: {}. Reason: {}",
        command.getCorrelationId(),
        e.getMessage()
      );
      throw new RuntimeException(
        "Business error processing command " + command.getCorrelationId(),
        e
      );
    } catch (Exception e) {
      log.error(
        "Unexpected error processing ReserveFundsCommand with correlationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Unexpected error processing command " + command.getCorrelationId(),
        e
      );
    }
  }

  @JmsListener(
    destination = JmsConfig.ACCOUNT_COMPLETE_TRANSFER_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleCompleteTransferCommand(
    @Payload CompleteTransferCommand command
  ) {
    log.info(
      "Received and converted JMS message on queue '{}': {}",
      JmsConfig.ACCOUNT_COMPLETE_TRANSFER_QUEUE,
      command
    );
    try {
      // Вызываем новый метод сервиса для завершения перевода
      accountService.completeTransfer(
        command.getSenderPhoneNumber(),
        command.getRecipientPhoneNumber(),
        command.getAmount(),
        command.getCorrelationId() // Передаем ID для логов/отслеживания
      );
      log.info(
        "Successfully processed CompleteTransferCommand with correlationId: {}",
        command.getCorrelationId()
      );
    } catch (
      AccountNotFoundException
      | InsufficientFundsException
      | IllegalStateException e
    ) {
      // IllegalStateException может быть из проверки резерва
      log.warn(
        "Business error processing CompleteTransferCommand with correlationId: {}. Reason: {}",
        command.getCorrelationId(),
        e.getMessage()
      );
      // Откатываем транзакцию
      throw new RuntimeException(
        "Business error processing command " + command.getCorrelationId(),
        e
      );
    } catch (Exception e) {
      log.error(
        "Unexpected error processing CompleteTransferCommand with correlationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      // Откатываем транзакцию
      throw new RuntimeException(
        "Unexpected error processing command " + command.getCorrelationId(),
        e
      );
    }
  }

  @JmsListener(
    destination = JmsConfig.ACCOUNT_RELEASE_FUNDS_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleReleaseFundsCommand(@Payload ReleaseFundsCommand command) {
    log.info(
      "Received and converted JMS message on queue '{}': {}",
      JmsConfig.ACCOUNT_RELEASE_FUNDS_QUEUE,
      command
    );
    try {
      accountService.releaseFunds(
        command.getPhoneNumber(),
        command.getAmount(),
        command.getCorrelationId()
      );
      log.info(
        "Successfully processed ReleaseFundsCommand with correlationId: {}",
        command.getCorrelationId()
      );
    } catch (AccountNotFoundException e) {
      // Важно: Что делать, если счет не найден при попытке отменить резерв?
      // Вероятно, это ошибка, но не критичная для отката. Просто логируем.
      log.warn(
        "Account not found while processing ReleaseFundsCommand with correlationId: {}. Reason: {}",
        command.getCorrelationId(),
        e.getMessage()
      );
      // Не бросаем исключение, чтобы JTA закоммитила транзакцию (сообщение будет удалено)
    } catch (Exception e) {
      log.error(
        "Unexpected error processing ReleaseFundsCommand with correlationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      // Откатываем транзакцию при неожиданных ошибках
      throw new RuntimeException(
        "Unexpected error processing command " + command.getCorrelationId(),
        e
      );
    }
  }
}
