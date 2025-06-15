package sbp.account.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import sbp.account.service.AccountService;
import sbp.dto.JmsQueueNames;
import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.ReserveFundsCommand;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class AccountCommandListener {

  private final AccountService accountService;

  @JmsListener(
    destination = JmsQueueNames.ACCOUNT_RESERVE_FUNDS_CMD_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleReserveFundsCommand(@Payload ReserveFundsCommand command) {
    log.info(
      "Received ReserveFundsCommand: CorrelationId={}, Sender={}, Amount={}",
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
      command.getAmount()
    );
    try {
      // Вызов @Transactional метода сервиса.
      // Если accountService.reserveFunds() выбросит исключение, JTA-транзакция (начатая слушателем) откатится.
      // Это включает откат изменений в БД и откат отправки сообщений, сделанных внутри reserveFunds().
      accountService.reserveFunds(command);
      log.info(
        "Successfully processed ReserveFundsCommand for CorrelationId: {}",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "Error processing ReserveFundsCommand for CorrelationId: {}. Reason: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Error processing ReserveFundsCommand: " + e.getMessage(),
        e
      );
    }
  }

  @JmsListener(
    destination = JmsQueueNames.ACCOUNT_RELEASE_FUNDS_CMD_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleReleaseFundsCommand(@Payload ReleaseFundsCommand command) {
    log.info(
      "Received ReleaseFundsCommand: CorrelationId={}, Sender={}, Amount={}, IsFinalDebit={}",
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
      command.getAmount(),
      command.isFinalDebit()
    );
    try {
      accountService.processFundsReleaseOrDebit(command);
      log.info(
        "Successfully processed ReleaseFundsCommand for CorrelationId: {}",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "Error processing ReleaseFundsCommand for CorrelationId: {}. Reason: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Error processing ReleaseFundsCommand: " + e.getMessage(),
        e
      );
    }
  }
}
