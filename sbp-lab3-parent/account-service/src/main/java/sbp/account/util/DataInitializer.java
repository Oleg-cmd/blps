package sbp.account.util;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import sbp.account.entity.Account;
import sbp.account.repository.AccountRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

  private final AccountRepository accountRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (accountRepository.findByPhoneNumber("9991112222").isEmpty()) {
      log.info("Initializing test accounts data...");

      Account account1 = Account.builder()
        .phoneNumber("9991112222") // "Основной" тестовый отправитель
        .balance(new BigDecimal("10000.00"))
        .reservedAmount(BigDecimal.ZERO)
        .email("sender@example.com")
        .build();

      Account account2 = Account.builder()
        .phoneNumber("9992223333") // "Основной" тестовый получатель
        .balance(new BigDecimal("5000.00"))
        .reservedAmount(BigDecimal.ZERO)
        .email("recipient.main@example.com")
        .build();

      Account account3 = Account.builder()
        .phoneNumber("9990000000") // Счет с маленьким балансом
        .balance(new BigDecimal("25.50"))
        .reservedAmount(BigDecimal.ZERO)
        .email("low.balance.user@example.com")
        .build();

      Account account4 = Account.builder()
        .phoneNumber("9994445555")
        .balance(new BigDecimal("1500.00"))
        .reservedAmount(new BigDecimal("100.00")) // с начальным резервом
        .email("another.user@example.com")
        .build();

      Account accountSberRecipient = Account.builder()
        .phoneNumber("9993334444") // RECIPIENT_PHONE_SBER
        .balance(new BigDecimal("2000.00"))
        .reservedAmount(BigDecimal.ZERO)
        .email("sber.recipient@example.com")
        .build();

      Account accountTinkoffRecipient = Account.builder()
        .phoneNumber("9995556666") // RECIPIENT_PHONE_TINKOFF
        .balance(new BigDecimal("3000.00"))
        .reservedAmount(BigDecimal.ZERO)
        .email("tinkoff.recipient@example.com")
        .build();

      accountRepository.saveAll(
        List.of(
          account1,
          account2,
          account3,
          account4,
          accountSberRecipient,
          accountTinkoffRecipient
        )
      );
      log.info("Test accounts initialized.");
    } else {
      log.info("Test accounts already exist, skipping initialization.");
    }
  }
}
