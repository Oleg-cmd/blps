package ru.sberbank.sbp.lab2.account_service.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import ru.sberbank.sbp.lab2.account_service.entity.Account;
import ru.sberbank.sbp.lab2.account_service.exception.AccountNotFoundException;
import ru.sberbank.sbp.lab2.account_service.exception.InsufficientFundsException;
import ru.sberbank.sbp.lab2.account_service.repository.AccountRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

  private final AccountRepository accountRepository;

  // Проверка баланса - операция чтения
  @Override
  @Transactional // Указываем, что транзакция только для чтения
  public boolean hasEnoughBalance(String phoneNumber, BigDecimal amount) {
    log.debug("Checking balance for {} amount {}", phoneNumber, amount);
    Account account = getAccountByPhoneNumber(phoneNumber);
    // Доступный баланс = Общий баланс - Зарезервированная сумма
    BigDecimal availableBalance = account
      .getBalance()
      .subtract(account.getReservedAmount());
    boolean enough = availableBalance.compareTo(amount) >= 0;
    log.debug(
      "Available balance for {}: {}. Enough: {}",
      phoneNumber,
      availableBalance,
      enough
    );
    return enough;
  }

  // Резервирование средств - операция записи
  @Override
  @Transactional
  public void reserveFunds(String phoneNumber, BigDecimal amount) {
    log.info("Reserving funds {} for phone number: {}", amount, phoneNumber);
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Cannot reserve negative amount");
    }
    Account account = getAccountByPhoneNumber(phoneNumber);

    if (!hasEnoughBalance(phoneNumber, amount)) {
      log.warn("Insufficient funds for phone number: {}", phoneNumber);
      throw new InsufficientFundsException(
        "Insufficient funds for phone number: " + phoneNumber
      );
    }

    account.setReservedAmount(account.getReservedAmount().add(amount));
    accountRepository.save(account); // Сохраняем изменения
    log.info(
      "Funds reserved successfully for {}. New reserved amount: {}",
      phoneNumber,
      account.getReservedAmount()
    );
  }

  // Только для внутреннего использования
  @Transactional
  protected void releaseReservedFundsInternal(
    String phoneNumber,
    BigDecimal amount
  ) {
    Account account = getAccountByPhoneNumber(phoneNumber);
    BigDecimal newReserved = account.getReservedAmount().subtract(amount);
    if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
      log.error(
        "Attempted to release more funds ({}) than reserved ({}) for {}",
        amount,
        account.getReservedAmount(),
        phoneNumber
      );
      newReserved = BigDecimal.ZERO;
    }
    account.setReservedAmount(newReserved);
    // Не сохраняем здесь, сохранение будет в вызывающем методе (completeTransfer)
    log.info(
      "Internal releaseReservedFunds: New reserved amount for {}: {}",
      phoneNumber,
      newReserved
    );
  }

  @Override
  @Transactional // Участвует в JTA транзакции слушателя
  public void releaseFunds(
    String phoneNumber,
    BigDecimal amount,
    UUID correlationId
  ) {
    log.info(
      "[CorrelationId: {}] Releasing reserved funds {} for phone number: {}",
      correlationId,
      amount,
      phoneNumber
    );
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      // Можно просто залогировать и выйти, т.к. это не ошибка бизнес-процесса
      log.warn(
        "[CorrelationId: {}] Cannot release negative amount: {}",
        correlationId,
        amount
      );
      return;
    }

    try {
      Account account = getAccountByPhoneNumber(phoneNumber); // Ищем счет

      BigDecimal newReserved = account.getReservedAmount().subtract(amount);
      if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
        log.warn(
          // Логируем как WARN, т.к. это может быть при повторной обработке
          "[CorrelationId: {}] Attempted to release more funds ({}) than reserved ({}) for {}. Setting reserved to 0.",
          correlationId,
          amount,
          account.getReservedAmount(),
          phoneNumber
        );
        newReserved = BigDecimal.ZERO;
      }
      account.setReservedAmount(newReserved);
      accountRepository.save(account); // Сохраняем изменения
      log.info(
        "[CorrelationId: {}] Reserved funds released successfully for {}. New reserved amount: {}",
        correlationId,
        phoneNumber,
        account.getReservedAmount()
      );
    } catch (AccountNotFoundException e) {
      // Если счет не найден - это нормально в этом сценарии (возможно, резерв и не создавался).
      // Просто логируем и НЕ бросаем исключение дальше, чтобы JTA закоммитила транзакцию JMS.
      log.warn(
        "[CorrelationId: {}] Account not found while trying to release funds for phone number: {}. Assuming reservation was not made or already cancelled.",
        correlationId,
        phoneNumber
      );
    }
    // Другие RuntimeException будут пойманы и обработаны в слушателе
  }

  @Override
  @Transactional
  public void completeTransfer(
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount,
    UUID correlationId
  ) {
    log.info(
      "[CorrelationId: {}] Completing transfer of {} from {} to {}",
      correlationId,
      amount,
      senderPhoneNumber,
      recipientPhoneNumber
    );
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Cannot transfer negative amount");
    }

    // 1. Найти счет отправителя (он должен существовать)
    Account senderAccount = getAccountByPhoneNumber(senderPhoneNumber);

    // 2. Проверить зарезервированную сумму отправителя
    // Эта проверка остается важной на случай гонки или неконсистентности
    if (senderAccount.getReservedAmount().compareTo(amount) < 0) {
      log.error(
        "[CorrelationId: {}] Attempt to complete transfer for sender {}, but reserved amount {} is less than transfer amount {}",
        correlationId,
        senderPhoneNumber,
        senderAccount.getReservedAmount(),
        amount
      );
      throw new IllegalStateException(
        "Cannot complete transfer, inconsistent reserved amount for sender: " +
        senderPhoneNumber
      );
    }

    // 3. Найти счет получателя (он ТОЖЕ должен существовать)
    Account recipientAccount = getAccountByPhoneNumber(recipientPhoneNumber);

    // 4. Выполнить дебет отправителя
    // Сначала вызываем внутренний метод для уменьшения резерва (без сохранения)
    releaseReservedFundsInternal(senderPhoneNumber, amount);
    // Затем уменьшаем баланс
    senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
    log.info(
      "[CorrelationId: {}] Debited amount {} from sender {}. New balance: {}, New reserved: {}",
      correlationId,
      amount,
      senderPhoneNumber,
      senderAccount.getBalance(),
      senderAccount.getReservedAmount()
    );

    // 5. Выполнить кредит получателя
    recipientAccount.setBalance(recipientAccount.getBalance().add(amount));
    log.info(
      "[CorrelationId: {}] Credited amount {} to recipient {}. New balance: {}",
      correlationId,
      amount,
      recipientPhoneNumber,
      recipientAccount.getBalance()
    );

    // 6. Сохранить оба измененных счета АТОМАРНО
    // Передаем обновленные объекты senderAccount и recipientAccount
    accountRepository.saveAll(Arrays.asList(senderAccount, recipientAccount));
    log.info(
      "[CorrelationId: {}] Successfully saved changes for sender {} and recipient {}",
      correlationId,
      senderPhoneNumber,
      recipientPhoneNumber
    );
  }

  @Override
  @Transactional
  public BigDecimal getBalance(String phoneNumber) {
    return getAccountByPhoneNumber(phoneNumber).getBalance();
  }

  @Override
  @Transactional
  public BigDecimal getReservedAmount(String phoneNumber) {
    return getAccountByPhoneNumber(phoneNumber).getReservedAmount();
  }

  // Вспомогательный приватный метод для получения счета
  private Account getAccountByPhoneNumber(String phoneNumber) {
    return accountRepository
      .findByPhoneNumber(phoneNumber)
      .orElseThrow(() -> {
        log.warn("Account not found for phone number: {}", phoneNumber);
        return new AccountNotFoundException(
          "Account not found for phone number: " + phoneNumber
        );
      });
  }
}
