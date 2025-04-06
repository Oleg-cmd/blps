package ru.sberbank.sbp.lab2.account_service.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import ru.sberbank.sbp.lab2.account_service.entity.Account;
import ru.sberbank.sbp.lab2.account_service.exception.AccountNotFoundException;
import ru.sberbank.sbp.lab2.account_service.exception.InsufficientFundsException;
import ru.sberbank.sbp.lab2.account_service.repository.AccountRepository;

@Service // Помечаем класс как сервис Spring
@RequiredArgsConstructor // Lombok: создает конструктор для final полей
@Slf4j // Lombok: добавляет логгер
public class AccountServiceImpl implements AccountService {

  private final AccountRepository accountRepository;

  // Базовая реализация поиска или создания счета
  // Делаем transactional, чтобы поиск и создание были атомарны
  @Override
  @Transactional // propagation = REQUIRED - стандартное поведение
  public Account findOrCreateAccount(String phoneNumber) {
    log.info("Finding or creating account for phone number: {}", phoneNumber);
    // synchronized - простой способ избежать гонки при создании одного и того же счета
    // В реальной системе нужна более надежная блокировка на уровне БД или внешняя
    synchronized (phoneNumber.intern()) {
      return accountRepository
        .findByPhoneNumber(phoneNumber)
        .orElseGet(() -> {
          log.info("Account not found, creating a new one for {}", phoneNumber);
          // Устанавливаем начальный баланс, например 100000
          Account newAccount = Account.builder()
            .phoneNumber(phoneNumber)
            .balance(new BigDecimal("100000.00")) // Начальный баланс для тестов
            .reservedAmount(BigDecimal.ZERO)
            .build();
          return accountRepository.save(newAccount);
        });
    }
  }

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

  // Освобождение резерва - операция записи
  @Override
  @Transactional
  public void releaseReservedFunds(String phoneNumber, BigDecimal amount) {
    log.info(
      "Releasing reserved funds {} for phone number: {}",
      amount,
      phoneNumber
    );
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Cannot release negative amount");
    }
    Account account = getAccountByPhoneNumber(phoneNumber);

    BigDecimal newReserved = account.getReservedAmount().subtract(amount);
    if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
      log.error(
        "Attempted to release more funds ({}) than reserved ({}) for {}",
        amount,
        account.getReservedAmount(),
        phoneNumber
      );
      // Можно выбросить исключение или просто установить в 0
      newReserved = BigDecimal.ZERO;
      // throw new IllegalStateException("Cannot release more funds than reserved for " + phoneNumber);
    }

    account.setReservedAmount(newReserved);
    accountRepository.save(account);
    log.info(
      "Reserved funds released successfully for {}. New reserved amount: {}",
      phoneNumber,
      account.getReservedAmount()
    );
  }

  // Завершение перевода - операция записи (пока без получателя)
  // ВАЖНО: Эта реализация НЕ атомарна для двух счетов без JTA!
  // Пока сделаем только списание с отправителя.
  @Override
  @Transactional
  public void completeFundsTransfer(
    String senderPhoneNumber,
    String recipientPhoneNumber,
    BigDecimal amount
  ) {
    log.info(
      "Completing transfer of {} from {} to {}",
      amount,
      senderPhoneNumber,
      recipientPhoneNumber
    );
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Cannot transfer negative amount");
    }

    Account senderAccount = getAccountByPhoneNumber(senderPhoneNumber);

    // Проверяем, достаточно ли зарезервировано (хотя release должен был проверить)
    if (senderAccount.getReservedAmount().compareTo(amount) < 0) {
      log.error(
        "Attempt to complete transfer for {}, but reserved amount {} is less than transfer amount {}",
        senderPhoneNumber,
        senderAccount.getReservedAmount(),
        amount
      );
      throw new IllegalStateException(
        "Cannot complete transfer, inconsistent reserved amount for sender: " +
        senderPhoneNumber
      );
    }

    // Уменьшаем резерв и баланс отправителя
    senderAccount.setReservedAmount(
      senderAccount.getReservedAmount().subtract(amount)
    );
    senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
    accountRepository.save(senderAccount);
    log.info(
      "Debited amount {} from sender {}. New balance: {}, New reserved: {}",
      amount,
      senderPhoneNumber,
      senderAccount.getBalance(),
      senderAccount.getReservedAmount()
    );

    // TODO: Зачисление получателю - будет добавлено, когда настроим JTA
    // Account recipientAccount = findOrCreateAccount(recipientPhoneNumber); // Найти или создать
    // recipientAccount.setBalance(recipientAccount.getBalance().add(amount));
    // accountRepository.save(recipientAccount);
    log.warn(
      "Recipient crediting for {} is NOT implemented yet!",
      recipientPhoneNumber
    );

    log.info(
      "Funds transfer (debit part) completed for sender {}",
      senderPhoneNumber
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
