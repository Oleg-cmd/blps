package sbp.account.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import sbp.account.entity.Account;
import sbp.account.repository.AccountRepository;
import sbp.dto.JmsQueueNames;
import sbp.dto.jms.CompleteTransferCommand;
import sbp.dto.jms.FundsProcessedEvent;
import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.ReserveFundsCommand;
import sbp.dto.jms.SendConfirmationCodeCommand;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

  private final AccountRepository accountRepository;
  private final JmsTemplate jmsTemplate;

  @Override
  @Transactional
  public void reserveFunds(ReserveFundsCommand command) {
    log.info(
      "Reserving funds: correlationId={}, account={}, amount={}",
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
      command.getAmount()
    );

    Account account = accountRepository
      .findByPhoneNumber(command.getSenderPhoneNumber())
      .orElseThrow(() -> {
        // Отправляем событие о неудаче, если аккаунт не найден
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          null, // recipientPhoneNumber не известен
          null, // recipientEmail не известен
          command.getAmount(),
          false, // success = false
          "Sender account not found: " + command.getSenderPhoneNumber()
        );
        log.error(
          "Account not found for phone number: {}",
          command.getSenderPhoneNumber()
        );
        return new RuntimeException(
          "Account not found: " + command.getSenderPhoneNumber()
        );
      });

    BigDecimal availableBalance = account
      .getBalance()
      .subtract(account.getReservedAmount());
    if (availableBalance.compareTo(command.getAmount()) < 0) {
      sendFundsProcessedEventInternal(
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        null, // recipientPhoneNumber
        null, // recipientEmail
        command.getAmount(),
        false, // success = false
        "Insufficient funds for account: " + command.getSenderPhoneNumber()
      );
      log.error(
        "Insufficient funds for account: {}. Available: {}, Requested: {}",
        command.getSenderPhoneNumber(),
        availableBalance,
        command.getAmount()
      );
      throw new RuntimeException(
        "Insufficient funds for account: " + command.getSenderPhoneNumber()
      );
    }

    if (
      command.getConfirmationCode() == null ||
      command.getConfirmationCode().isBlank()
    ) {
      // Теоретически, это не должно произойти, так как TransferService генерирует код.
      sendFundsProcessedEventInternal(
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        null, // recipientPhoneNumber
        null, // recipientEmail
        command.getAmount(),
        false, // success = false
        "Confirmation code is missing in ReserveFundsCommand."
      );
      log.error(
        "Confirmation code is missing in ReserveFundsCommand for correlationId: {}",
        command.getCorrelationId()
      );
      throw new IllegalStateException(
        "Confirmation code is mandatory for reserving funds."
      );
    }

    account.setReservedAmount(
      account.getReservedAmount().add(command.getAmount())
    );
    accountRepository.save(account);
    log.info(
      "Funds reserved for account: {}, new reserved: {}",
      command.getSenderPhoneNumber(),
      account.getReservedAmount()
    );

    // Отправка команды на отправку кода подтверждения
    SendConfirmationCodeCommand codeCommand =
      SendConfirmationCodeCommand.builder()
        .correlationId(command.getCorrelationId())
        .phoneNumber(command.getSenderPhoneNumber()) // Код отправляется отправителю
        .code(command.getConfirmationCode())
        .build();
    sendToNotificationService(codeCommand);

    // Отправка события об успешном резервировании
    sendFundsProcessedEventInternal(
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
      null, // recipientPhoneNumber не актуален для события резервирования
      null, // recipientEmail не актуален для события резервирования
      command.getAmount(),
      true, // success = true
      null // reason
    );
  }

  @Override
  @Transactional
  public void processFundsReleaseOrDebit(ReleaseFundsCommand command) {
    log.info(
      "Processing funds release/debit: correlationId={}, sender={}, recipient={}, amount={}, finalDebit={}",
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
      command.getRecipientPhoneNumber(),
      command.getAmount(),
      command.isFinalDebit()
    );

    Account senderAccount = accountRepository
      .findByPhoneNumber(command.getSenderPhoneNumber())
      .orElseThrow(() -> {
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          null, // recipientEmail не известен, если отправитель не найден
          command.getAmount(),
          false,
          "Sender account not found: " + command.getSenderPhoneNumber()
        );
        log.error(
          "Sender account not found: {}",
          command.getSenderPhoneNumber()
        );
        return new RuntimeException(
          "Sender account not found: " + command.getSenderPhoneNumber()
        );
      });

    String recipientEmail = null;
    Account recipientAccount = null;

    if (command.isFinalDebit()) {
      // --- Финальное списание ---
      if (
        command.getRecipientPhoneNumber() == null ||
        command.getRecipientPhoneNumber().isBlank()
      ) {
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          null, // recipientEmail
          command.getAmount(),
          false,
          "Recipient phone number is null or blank for final debit."
        );
        log.error(
          "Recipient phone number is null or blank for final debit. CorrelationId: {}",
          command.getCorrelationId()
        );
        throw new IllegalArgumentException(
          "Recipient phone number cannot be null or blank for final debit."
        );
      }

      // Получаем или создаем аккаунт получателя
      recipientAccount = accountRepository
        .findByPhoneNumber(command.getRecipientPhoneNumber())
        .orElseGet(() -> {
          log.warn(
            "Recipient account {} not found, creating new one with default email. CorrelationId: {}",
            command.getRecipientPhoneNumber(),
            command.getCorrelationId()
          );
          Account newRecipient = new Account(command.getRecipientPhoneNumber());
          newRecipient.setEmail(
            "recipient." + command.getRecipientPhoneNumber() + "@example.com"
          );
          return accountRepository.save(newRecipient);
        });

      recipientEmail = recipientAccount.getEmail();

      if (
        senderAccount.getReservedAmount().compareTo(command.getAmount()) < 0
      ) {
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          recipientEmail,
          command.getAmount(),
          false,
          "Inconsistent reserved amount for final debit. Reserved: " +
          senderAccount.getReservedAmount() +
          ", Requested: " +
          command.getAmount()
        );
        log.error(
          "Reserved amount {} < debit amount {} for account {}. CorrelationId: {}",
          senderAccount.getReservedAmount(),
          command.getAmount(),
          senderAccount.getPhoneNumber(),
          command.getCorrelationId()
        );
        throw new IllegalStateException(
          "Inconsistent reserved amount for final debit."
        );
      }

      // Операции с БД: списание у отправителя
      senderAccount.setReservedAmount(
        senderAccount.getReservedAmount().subtract(command.getAmount())
      );
      senderAccount.setBalance(
        senderAccount.getBalance().subtract(command.getAmount())
      );
      accountRepository.save(senderAccount);
      log.info(
        "Debited from sender: {}, new balance: {}, new reserved: {}",
        senderAccount.getPhoneNumber(),
        senderAccount.getBalance(),
        senderAccount.getReservedAmount()
      );

      // Операции с БД: зачисление получателю
      recipientAccount.setBalance(
        recipientAccount.getBalance().add(command.getAmount())
      );
      accountRepository.save(recipientAccount);
      log.info(
        "Credited to recipient: {}, new balance: {}",
        recipientAccount.getPhoneNumber(),
        recipientAccount.getBalance()
      );

      // Отправляем событие об успешной обработке средств
      sendFundsProcessedEventInternal(
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        command.getRecipientPhoneNumber(),
        recipientEmail,
        command.getAmount(),
        true, // success = true
        null // reason
      );

      // Отправляем команду о завершении трансфера
      sendCompleteTransferCommandInternal(
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        command.getRecipientPhoneNumber(),
        command.getAmount()
      );
    } else {
      // --- Отмена резерва ---
      BigDecimal newReservedAmount = senderAccount
        .getReservedAmount()
        .subtract(command.getAmount());
      if (newReservedAmount.compareTo(BigDecimal.ZERO) < 0) {
        log.warn(
          "Attempt to release more funds ({}) than reserved ({}) for account {}. Setting reserve to 0.",
          command.getAmount(),
          senderAccount.getReservedAmount(),
          senderAccount.getPhoneNumber()
        );
        newReservedAmount = BigDecimal.ZERO;
      }
      senderAccount.setReservedAmount(newReservedAmount);
      accountRepository.save(senderAccount);
      log.info(
        "Reservation cancelled/reduced for account: {}. New reserved: {}",
        senderAccount.getPhoneNumber(),
        senderAccount.getReservedAmount()
      );

      // Отправляем FundsProcessedEvent об отмене резерва
      sendFundsProcessedEventInternal(
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        command.getRecipientPhoneNumber(),
        null, // Email получателя не актуален для отмены резерва
        command.getAmount(),
        false, // success = false, т.к. это отмена, а не успешное завершение
        "Funds reservation cancelled/rolled back."
      );
    }
  }

  private void sendToNotificationService(SendConfirmationCodeCommand command) {
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.NOTIFICATION_SEND_CODE_CMD_QUEUE,
        command
      );
      log.info(
        "Sent SendConfirmationCodeCommand for correlationId: {}",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "Failed to send SendConfirmationCodeCommand for correlationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Failed to send confirmation code command to NotificationService",
        e
      );
    }
  }

  private void sendFundsProcessedEventInternal(
    UUID correlationId,
    String senderPhoneNumber,
    String recipientPhoneNumber, // Может быть null
    String recipientEmail, // Может быть null
    BigDecimal amount,
    boolean success,
    String reason
  ) {
    FundsProcessedEvent event = FundsProcessedEvent.builder()
      .correlationId(correlationId)
      .senderPhoneNumber(senderPhoneNumber)
      .recipientPhoneNumber(recipientPhoneNumber)
      .recipientEmail(recipientEmail) // Устанавливаем email
      .amount(amount)
      .success(success)
      .reason(reason)
      .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE,
        event
      ); // Очередь для transfer-service
      log.info(
        "Sent FundsProcessedEvent: correlationId={}, success={}, recipientEmail={}",
        event.getCorrelationId(),
        event.isSuccess(),
        event.getRecipientEmail()
      );
    } catch (Exception e) {
      log.error(
        "Failed to send FundsProcessedEvent for correlationId: {}. Error: {}",
        event.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Failed to send FundsProcessedEvent to TransferService",
        e
      );
    }
  }

  private void sendCompleteTransferCommandInternal(
    UUID correlationId,
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount
  ) {
    CompleteTransferCommand command = CompleteTransferCommand.builder()
      .correlationId(correlationId)
      .senderPhoneNumber(senderPhoneNumber)
      .recipientPhoneNumber(recipientPhoneNumber)
      .amount(amount)
      .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.ACCOUNT_TRANSFER_COMPLETED_EVENT_QUEUE,
        command
      );
      log.info(
        "Sent CompleteTransferCommand: correlationId={}, sender={}, recipient={}, amount={}",
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        command.getRecipientPhoneNumber(),
        command.getAmount()
      );
    } catch (Exception e) {
      log.error(
        "Failed to send CompleteTransferCommand for correlationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException("Failed to send CompleteTransferCommand", e);
    }
  }
}
