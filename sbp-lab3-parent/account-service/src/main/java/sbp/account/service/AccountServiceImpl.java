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
  @Transactional // Эта аннотация управляет JTA транзакцией
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
        log.error(
          "Account not found for phone number: {}",
          command.getSenderPhoneNumber()
        );
        return new RuntimeException(
          // Это приведет к откату JTA транзакции
          "Account not found: " + command.getSenderPhoneNumber()
        );
      });

    BigDecimal availableBalance = account
      .getBalance()
      .subtract(account.getReservedAmount());
    if (availableBalance.compareTo(command.getAmount()) < 0) {
      log.error(
        "Insufficient funds for account: {}. Available: {}, Requested: {}",
        command.getSenderPhoneNumber(),
        availableBalance,
        command.getAmount()
      );
      throw new RuntimeException(
        // Откатит JTA транзакцию
        "Insufficient funds for account: " + command.getSenderPhoneNumber()
      );
    }

    if (
      command.getConfirmationCode() == null ||
      command.getConfirmationCode().isBlank()
    ) {
      log.error(
        "Confirmation code is missing in ReserveFundsCommand for correlationId: {}",
        command.getCorrelationId()
      );
      throw new IllegalStateException(
        // Откатит JTA транзакцию
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

    SendConfirmationCodeCommand codeCommand =
      SendConfirmationCodeCommand.builder()
        .correlationId(command.getCorrelationId())
        .phoneNumber(command.getSenderPhoneNumber())
        .code(command.getConfirmationCode())
        .build();
    sendToNotificationService(codeCommand);

    sendFundsProcessedEventInternal(
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
      null,
      command.getAmount(),
      true,
      null
    );
  }

  @Override
  @Transactional // Эта аннотация управляет JTA транзакцией
  public void processFundsReleaseOrDebit(ReleaseFundsCommand command) {
    log.info(
      "Processing funds release/debit: correlationId={}, sender={}, amount={}, finalDebit={}",
      command.getCorrelationId(),
      command.getSenderPhoneNumber(),
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
          command.getAmount(),
          false, // success = false
          "Sender account not found: " + command.getSenderPhoneNumber()
        );
        log.error(
          "Sender account not found: {}",
          command.getSenderPhoneNumber()
        );
        return new RuntimeException(
          // Это приведет к откату JTA транзакции
          "Sender account not found: " + command.getSenderPhoneNumber()
        );
      });

    boolean isSuccessFinalDebit = false; // Флаг успеха для финального списания

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

      if (
        senderAccount.getReservedAmount().compareTo(command.getAmount()) < 0
      ) {
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          command.getAmount(),
          false,
          "Inconsistent reserved amount for final debit."
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
      Account recipientAccount = accountRepository
        .findByPhoneNumber(command.getRecipientPhoneNumber())
        .orElseGet(() -> {
          log.warn(
            "Recipient account {} not found, creating new one. CorrelationId: {}",
            command.getRecipientPhoneNumber(),
            command.getCorrelationId()
          );
          Account newRecipient = new Account();
          newRecipient.setPhoneNumber(command.getRecipientPhoneNumber());
          newRecipient.setBalance(BigDecimal.ZERO);
          newRecipient.setReservedAmount(BigDecimal.ZERO);
          return accountRepository.save(newRecipient);
        });

      recipientAccount.setBalance(
        recipientAccount.getBalance().add(command.getAmount())
      );
      accountRepository.save(recipientAccount);
      log.info(
        "Credited to recipient: {}, new balance: {}",
        recipientAccount.getPhoneNumber(),
        recipientAccount.getBalance()
      );

      isSuccessFinalDebit = true; // Все операции с БД для финального списания успешны
    } else {
      // --- Отмена резерва ---
      BigDecimal newReservedAmount = senderAccount
        .getReservedAmount()
        .subtract(command.getAmount());
      senderAccount.setReservedAmount(newReservedAmount.max(BigDecimal.ZERO));
      accountRepository.save(senderAccount);
      log.info(
        "Reservation cancelled/reduced for account: {}. New reserved: {}",
        senderAccount.getPhoneNumber(),
        senderAccount.getReservedAmount()
      );
    }

    if (command.isFinalDebit()) {
      if (isSuccessFinalDebit) {
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          command.getAmount(),
          true,
          null
        );
        sendCompleteTransferCommandInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          command.getAmount()
        );
      } else {
        sendFundsProcessedEventInternal(
          command.getCorrelationId(),
          command.getSenderPhoneNumber(),
          command.getRecipientPhoneNumber(),
          command.getAmount(),
          false,
          "Final debit failed due to an unexpected internal error."
        );
      }
    } else {
      // Отправляем FundsProcessedEvent об отмене резерва
      sendFundsProcessedEventInternal(
        command.getCorrelationId(),
        command.getSenderPhoneNumber(),
        command.getRecipientPhoneNumber(),
        command.getAmount(),
        false,
        "Funds reservation cancelled/rolled back."
      );
    }
  }

  // Внутренний метод для отправки SendConfirmationCodeCommand
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
      throw new RuntimeException("Failed to send confirmation code command", e); // Откатит JTA
    }
  }

  // Внутренний метод для отправки FundsProcessedEvent
  private void sendFundsProcessedEventInternal(
    UUID correlationId,
    String sender,
    String recipient,
    BigDecimal amount,
    boolean success,
    String reason
  ) {
    FundsProcessedEvent event = FundsProcessedEvent.builder()
      .correlationId(correlationId)
      .senderPhoneNumber(sender)
      .recipientPhoneNumber(recipient)
      .amount(amount)
      .success(success)
      .reason(reason)
      .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE, // Очередь для transfer-service
        event
      );
      log.info(
        "Sent FundsProcessedEvent: correlationId={}, success={}",
        event.getCorrelationId(),
        event.isSuccess()
      );
    } catch (Exception e) {
      log.error(
        "Failed to send FundsProcessedEvent for correlationId: {}. Error: {}",
        event.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException("Failed to send funds processed event", e); // Откатит JTA
    }
  }

  // Внутренний метод для отправки CompleteTransferCommand
  private void sendCompleteTransferCommandInternal(
    UUID correlationId,
    String sender,
    String recipient,
    BigDecimal amount
  ) {
    CompleteTransferCommand command = CompleteTransferCommand.builder()
      .correlationId(correlationId)
      .senderPhoneNumber(sender)
      .recipientPhoneNumber(recipient)
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
      throw new RuntimeException("Failed to send complete transfer command", e); // Откатит JTA
    }
  }
}
