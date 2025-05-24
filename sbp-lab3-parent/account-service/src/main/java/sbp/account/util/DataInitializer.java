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
        .build();

      Account account2 = Account.builder()
        .phoneNumber("9992223333") // "Основной" тестовый получатель
        .balance(new BigDecimal("5000.00"))
        .reservedAmount(BigDecimal.ZERO)
        .build();

      Account account3 = Account.builder()
        .phoneNumber("9990000000") // Счет с маленьким балансом для тестов нехватки
        .balance(new BigDecimal("25.50"))
        .reservedAmount(BigDecimal.ZERO)
        .build();

      Account account4 = Account.builder()
        .phoneNumber("9994445555") // Еще один счет
        .balance(new BigDecimal("1500.00"))
        .reservedAmount(new BigDecimal("100.00")) // с начальным резервом
        .build();

      accountRepository.saveAll(
        List.of(account1, account2, account3, account4)
      );
      log.info("Test accounts initialized.");
    } else {
      log.info("Test accounts already exist, skipping initialization.");
    }
  }
}
