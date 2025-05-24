package sbp.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

import jakarta.jms.ConnectionFactory;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.jta.JtaTransactionManager;
import sbp.account.entity.Account;
import sbp.account.repository.AccountRepository;
import sbp.account.service.AccountService;
import sbp.dto.JmsQueueNames;
import sbp.dto.jms.FundsProcessedEvent;
import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.ReserveFundsCommand;
import sbp.dto.jms.SendConfirmationCodeCommand;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class AccountServiceApplicationTests {

  @Autowired
  private AccountService accountService;

  @Autowired
  private AccountRepository accountRepository;

  @MockBean
  private JmsTemplate jmsTemplate;

  @MockBean
  private DefaultJmsListenerContainerFactoryConfigurer jmsListenerContainerFactoryConfigurer;

  @MockBean
  private ConnectionFactory connectionFactory;

  @Autowired
  private JtaTransactionManager jtaTransactionManager;

  private UserTransaction userTransaction; // Для явного управления транзакциями в тестах при необходимости

  private final String SENDER_PHONE_OK_BALANCE = "9991112222";
  private final String RECIPIENT_PHONE = "9992223333";
  private final String SENDER_PHONE_LOW_BALANCE = "9990000000";
  private final String SENDER_PHONE_WITH_RESERVE = "9994445555"; // Предполагается, что этот счет есть

  @BeforeEach
  void setUp() throws Exception {
    // Сбрасываем мок перед каждым тестом
    reset(jmsTemplate);

    // Получаем UserTransaction для возможного ручного управления (хотя @Transactional в сервисе должен работать)
    userTransaction = jtaTransactionManager.getUserTransaction();
    assertNotNull(
      userTransaction,
      "UserTransaction should not be null for JTA tests"
    );

    // Данные должны быть инициализированы DataInitializer (который использует @Transactional)
    // Дополнительная проверка и инициализация, если DataInitializer не отработал или для чистоты теста
    // В идеале, DataInitializer должен вызываться автоматически и создавать данные в тестовой H2 XA базе.
    // Убедимся, что счет, который мы ожидаем с резервом, существует и имеет резерв
    Optional<Account> reservedAccountOpt = accountRepository.findByPhoneNumber(
      SENDER_PHONE_WITH_RESERVE
    );
    if (
      reservedAccountOpt.isEmpty() ||
      reservedAccountOpt.get().getReservedAmount().compareTo(BigDecimal.ZERO) <=
      0
    ) {
      log.warn(
        "Account {} for reserved tests not found or has no reserve, ensuring test data...",
        SENDER_PHONE_WITH_RESERVE
      );
      Account accWithReserve = reservedAccountOpt.orElseGet(() ->
        Account.builder()
          .phoneNumber(SENDER_PHONE_WITH_RESERVE)
          .balance(new BigDecimal("2000.00"))
          .build()
      );
      accWithReserve.setReservedAmount(new BigDecimal("100.00")); // Устанавливаем резерв
      if (
        accWithReserve
          .getBalance()
          .compareTo(accWithReserve.getReservedAmount()) <
        0
      ) {
        accWithReserve.setBalance(
          accWithReserve.getReservedAmount().add(new BigDecimal("500"))
        ); // Баланс должен быть >= резерва
      }
      accountRepository.saveAndFlush(accWithReserve);
    }
    // Убедимся, что основной отправитель есть
    if (
      accountRepository.findByPhoneNumber(SENDER_PHONE_OK_BALANCE).isEmpty()
    ) {
      accountRepository.saveAndFlush(
        Account.builder()
          .phoneNumber(SENDER_PHONE_OK_BALANCE)
          .balance(new BigDecimal("10000.00"))
          .reservedAmount(BigDecimal.ZERO)
          .build()
      );
    }
    // Убедимся, что получатель есть
    if (accountRepository.findByPhoneNumber(RECIPIENT_PHONE).isEmpty()) {
      accountRepository.saveAndFlush(
        Account.builder()
          .phoneNumber(RECIPIENT_PHONE)
          .balance(new BigDecimal("5000.00"))
          .reservedAmount(BigDecimal.ZERO)
          .build()
      );
    }
    // Убедимся, что отправитель с малым балансом есть
    if (
      accountRepository.findByPhoneNumber(SENDER_PHONE_LOW_BALANCE).isEmpty()
    ) {
      accountRepository.saveAndFlush(
        Account.builder()
          .phoneNumber(SENDER_PHONE_LOW_BALANCE)
          .balance(new BigDecimal("25.50"))
          .reservedAmount(BigDecimal.ZERO)
          .build()
      );
    }

    assertTrue(
      accountRepository.findByPhoneNumber(SENDER_PHONE_OK_BALANCE).isPresent(),
      "Account " + SENDER_PHONE_OK_BALANCE + " should be initialized."
    );
    assertTrue(
      accountRepository
        .findByPhoneNumber(SENDER_PHONE_WITH_RESERVE)
        .isPresent(),
      "Account " +
      SENDER_PHONE_WITH_RESERVE +
      " should be initialized and have a reserve."
    );
    assertTrue(
      accountRepository
        .findByPhoneNumber(SENDER_PHONE_WITH_RESERVE)
        .get()
        .getReservedAmount()
        .compareTo(BigDecimal.ZERO) >
      0,
      SENDER_PHONE_WITH_RESERVE + " should have a positive reserved amount."
    );
  }

  @AfterEach
  void tearDown() throws Exception {
    // Попытка откатить транзакцию, если она активна, чтобы тесты не влияли друг на друга
    if (
      userTransaction != null &&
      userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION
    ) {
      try {
        log.warn(
          "JTA transaction was active (status: {}) at the end of the test. Forcing rollback.",
          userTransaction.getStatus()
        );
        userTransaction.rollback();
      } catch (Exception e) {
        log.error(
          "Error during forced JTA transaction rollback in tearDown",
          e
        );
      }
    }
  }

  @Test
  void contextLoads() {
    // Проверяет, что контекст Spring Boot загружается корректно
    assertNotNull(accountService);
    assertNotNull(accountRepository);
    assertNotNull(jmsTemplate); // Должен быть мок
    assertNotNull(jtaTransactionManager);
  }

  @Test
  void reserveFunds_whenSufficientBalance_shouldReserveAndSendCommand() {
    BigDecimal amountToReserve = new BigDecimal("100.00");
    UUID correlationId = UUID.randomUUID();
    String confirmationCode = "123456"; // Код передается в команде

    ReserveFundsCommand command = ReserveFundsCommand.builder()
      .senderPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .amount(amountToReserve)
      .correlationId(correlationId)
      .confirmationCode(confirmationCode) // Передаем код
      .build();

    Account accountBefore = accountRepository
      .findByPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .orElseThrow();
    BigDecimal initialBalance = accountBefore.getBalance();
    BigDecimal initialReserved = accountBefore.getReservedAmount();

    // Метод сервиса должен быть @Transactional, JTA транзакция начнется здесь
    accountService.reserveFunds(command);
    // JTA транзакция должна закоммититься здесь (если нет ошибок)

    Account accountAfter = accountRepository
      .findByPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .orElseThrow();
    assertEquals(
      0,
      initialReserved
        .add(amountToReserve)
        .compareTo(accountAfter.getReservedAmount()),
      "Reserved amount should be increased by " + amountToReserve
    );
    assertEquals(
      0,
      initialBalance.compareTo(accountAfter.getBalance()),
      "Balance should not change on reserve"
    );

    // Проверяем, что JmsTemplate.convertAndSend был вызван с правильными аргументами
    ArgumentCaptor<SendConfirmationCodeCommand> captor =
      ArgumentCaptor.forClass(SendConfirmationCodeCommand.class);
    verify(jmsTemplate, times(1)).convertAndSend(
      eq(JmsQueueNames.NOTIFICATION_SEND_CODE_CMD_QUEUE),
      captor.capture()
    );

    SendConfirmationCodeCommand sentJmsCommand = captor.getValue();
    assertEquals(correlationId, sentJmsCommand.getCorrelationId());
    assertEquals(SENDER_PHONE_OK_BALANCE, sentJmsCommand.getPhoneNumber());
    assertEquals(confirmationCode, sentJmsCommand.getCode());
  }

  @Test
  void reserveFunds_whenInsufficientBalance_shouldThrowExceptionAndRollback() {
    BigDecimal amountToReserve = new BigDecimal("100.00"); // Больше, чем SENDER_PHONE_LOW_BALANCE (25.50)
    UUID correlationId = UUID.randomUUID();

    ReserveFundsCommand command = ReserveFundsCommand.builder()
      .senderPhoneNumber(SENDER_PHONE_LOW_BALANCE)
      .amount(amountToReserve)
      .correlationId(correlationId)
      .confirmationCode("654321")
      .build();

    Account accountBefore = accountRepository
      .findByPhoneNumber(SENDER_PHONE_LOW_BALANCE)
      .orElseThrow();
    BigDecimal initialReserved = accountBefore.getReservedAmount();
    BigDecimal initialBalance = accountBefore.getBalance();

    // Ожидаем RuntimeException из-за нехватки средств
    Exception exception = assertThrows(RuntimeException.class, () -> {
      accountService.reserveFunds(command);
    });
    assertTrue(exception.getMessage().contains("Insufficient funds"));

    // Проверяем, что состояние счета не изменилось (откат)
    Account accountAfter = accountRepository
      .findByPhoneNumber(SENDER_PHONE_LOW_BALANCE)
      .orElseThrow();
    assertEquals(
      0,
      initialReserved.compareTo(accountAfter.getReservedAmount()),
      "Reserved amount should be rolled back"
    );
    assertEquals(
      0,
      initialBalance.compareTo(accountAfter.getBalance()),
      "Balance should be rolled back"
    );

    // Проверяем, что JMS сообщение не было отправлено (из-за отката JTA)
    verify(jmsTemplate, never()).convertAndSend(
      eq(JmsQueueNames.NOTIFICATION_SEND_CODE_CMD_QUEUE),
      any(SendConfirmationCodeCommand.class)
    );
  }

  @Test
  void reserveFunds_whenAccountNotFound_shouldThrowExceptionAndNotSendJms() {
    String nonExistentPhone = "0000000000";
    ReserveFundsCommand command = ReserveFundsCommand.builder()
      .senderPhoneNumber(nonExistentPhone)
      .amount(new BigDecimal("10.00"))
      .correlationId(UUID.randomUUID())
      .confirmationCode("111222")
      .build();

    Exception exception = assertThrows(RuntimeException.class, () -> {
      accountService.reserveFunds(command);
    });
    assertTrue(exception.getMessage().contains("Account not found"));

    // JMS не должен быть вызван, так как ошибка произошла до отправки
    verify(jmsTemplate, never()).convertAndSend(anyString(), any(Object.class));
  }

  @Test
  void processFundsReleaseOrDebit_whenFinalDebitSuccessful_shouldTransferFundsAndSendEvent() {
    BigDecimal amountToTransfer = new BigDecimal("70.00");
    UUID correlationId = UUID.randomUUID();

    // Устанавливаем начальный резерв для отправителя
    Account senderBefore = accountRepository
      .findByPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .orElseThrow();
    senderBefore.setReservedAmount(amountToTransfer); // Резервируем сумму перевода
    accountRepository.saveAndFlush(senderBefore); // Сохраняем изменения перед тестом

    BigDecimal senderInitialBalance = senderBefore.getBalance();
    BigDecimal senderInitialReserved = senderBefore.getReservedAmount();

    Account recipientBefore = accountRepository
      .findByPhoneNumber(RECIPIENT_PHONE)
      .orElseThrow();
    BigDecimal recipientInitialBalance = recipientBefore.getBalance();

    ReleaseFundsCommand command = ReleaseFundsCommand.builder()
      .senderPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .recipientPhoneNumber(RECIPIENT_PHONE) // Важно для дебета
      .amount(amountToTransfer)
      .correlationId(correlationId)
      .isFinalDebit(true) // Это финальное списание
      .build();

    accountService.processFundsReleaseOrDebit(command);

    // Проверяем балансы и резервы после
    Account senderAfter = accountRepository
      .findByPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .orElseThrow();
    assertEquals(
      0,
      senderInitialBalance
        .subtract(amountToTransfer)
        .compareTo(senderAfter.getBalance())
    );
    assertEquals(
      0,
      senderInitialReserved
        .subtract(amountToTransfer)
        .compareTo(senderAfter.getReservedAmount())
    ); // Резерв должен уменьшиться

    Account recipientAfter = accountRepository
      .findByPhoneNumber(RECIPIENT_PHONE)
      .orElseThrow();
    assertEquals(
      0,
      recipientInitialBalance
        .add(amountToTransfer)
        .compareTo(recipientAfter.getBalance())
    );

    // Проверяем отправку FundsProcessedEvent
    ArgumentCaptor<FundsProcessedEvent> captor = ArgumentCaptor.forClass(
      FundsProcessedEvent.class
    );
    verify(jmsTemplate, times(1)).convertAndSend(
      eq(JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE), // Обновленное имя очереди
      captor.capture()
    );
    FundsProcessedEvent event = captor.getValue();
    assertTrue(event.isSuccess());
    assertEquals(correlationId, event.getCorrelationId());
    assertEquals(SENDER_PHONE_OK_BALANCE, event.getSenderPhoneNumber());
    assertEquals(RECIPIENT_PHONE, event.getRecipientPhoneNumber());
  }

  @Test
  void processFundsReleaseOrDebit_whenCancelReservation_shouldReleaseReservedAndSendEvent() {
    BigDecimal amountToRelease = new BigDecimal("30.00");
    UUID correlationId = UUID.randomUUID();

    // Используем счет с предустановленным резервом
    Account senderBefore = accountRepository
      .findByPhoneNumber(SENDER_PHONE_WITH_RESERVE)
      .orElseThrow();
    BigDecimal initialBalance = senderBefore.getBalance();
    BigDecimal initialReserved = senderBefore.getReservedAmount(); // Должен быть > amountToRelease

    assertTrue(
      initialReserved.compareTo(amountToRelease) >= 0,
      "Initial reserve must be sufficient for release."
    );

    ReleaseFundsCommand command = ReleaseFundsCommand.builder()
      .senderPhoneNumber(SENDER_PHONE_WITH_RESERVE)
      // recipientPhoneNumber не нужен для isFinalDebit=false, но DTO его содержит
      .recipientPhoneNumber(null)
      .amount(amountToRelease)
      .correlationId(correlationId)
      .isFinalDebit(false) // Это отмена резерва
      .build();

    accountService.processFundsReleaseOrDebit(command);

    Account senderAfter = accountRepository
      .findByPhoneNumber(SENDER_PHONE_WITH_RESERVE)
      .orElseThrow();
    assertEquals(
      0,
      initialReserved
        .subtract(amountToRelease)
        .compareTo(senderAfter.getReservedAmount())
    );
    assertEquals(0, initialBalance.compareTo(senderAfter.getBalance())); // Баланс не меняется

    ArgumentCaptor<FundsProcessedEvent> captor = ArgumentCaptor.forClass(
      FundsProcessedEvent.class
    );
    verify(jmsTemplate, times(1)).convertAndSend(
      eq(JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE), // Обновленное имя очереди
      captor.capture()
    );
    FundsProcessedEvent event = captor.getValue();
    assertFalse(event.isSuccess());
    assertEquals(correlationId, event.getCorrelationId());
    assertEquals("Funds reservation cancelled/rolled back.", event.getReason());
  }

  @Test
  void processFundsReleaseOrDebit_finalDebit_recipientNotFound_shouldThrowAndRollbackJms() {
    String nonExistentRecipientPhone = "0000000000"; // Не существующий получатель
    BigDecimal amountToTransfer = new BigDecimal("50.00");
    UUID correlationId = UUID.randomUUID();

    Account senderBefore = accountRepository
      .findByPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .orElseThrow();
    // Убедимся, что у отправителя достаточно средств и есть что резервировать
    senderBefore.setBalance(new BigDecimal("1000.00"));
    senderBefore.setReservedAmount(amountToTransfer); // Резервируем сумму для теста
    accountRepository.saveAndFlush(senderBefore);

    BigDecimal originalSenderBalance = senderBefore.getBalance();
    BigDecimal originalSenderReserved = senderBefore.getReservedAmount();

    ReleaseFundsCommand command = ReleaseFundsCommand.builder()
      .senderPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .recipientPhoneNumber(nonExistentRecipientPhone)
      .amount(amountToTransfer)
      .correlationId(correlationId)
      .isFinalDebit(true)
      .build();

    // Ожидаем RuntimeException (например, из-за AccountNotFoundException для получателя)
    Exception exception = assertThrows(RuntimeException.class, () -> {
      accountService.processFundsReleaseOrDebit(command);
    });
    assertTrue(exception.getMessage().contains("Recipient account not found"));

    // Проверяем, что состояние счета отправителя откатилось
    Account senderAfterRollback = accountRepository
      .findByPhoneNumber(SENDER_PHONE_OK_BALANCE)
      .orElseThrow();
    assertEquals(
      0,
      originalSenderBalance.compareTo(senderAfterRollback.getBalance()),
      "Sender balance should be rolled back."
    );
    assertEquals(
      0,
      originalSenderReserved.compareTo(senderAfterRollback.getReservedAmount()),
      "Sender reserved amount should be rolled back."
    );

    // Проверяем, что JMS сообщение FundsProcessedEvent НЕ было отправлено (или его отправка была откатана)
    // Так как AccountServiceImpl сейчас отправляет событие *перед* выбрасыванием исключения,
    // и это событие должно быть частью транзакции, оно откатится.
    verify(jmsTemplate, never()).convertAndSend(
      eq(JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE),
      any(FundsProcessedEvent.class)
    );
  }
}
