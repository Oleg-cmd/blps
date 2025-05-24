package sbp.adapter.config;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;
import sbp.dto.rest.BankInfo;

@Component
@Getter // Автоматически создаст геттеры для всех полей
public class MockSbpDataConfig {

  // --- Константы для детерминированных ошибок ---
  public static final BigDecimal BUSINESS_ERROR_AMOUNT = new BigDecimal(
    "422.00"
  );
  public static final BigDecimal TECHNICAL_ERROR_AMOUNT = new BigDecimal(
    "500.00"
  );
  public static final String UNKNOWN_PHONE_FOR_BANK_SEARCH = "9000000000";

  // --- Реестры данных ---
  private final Map<String, BankInfo> bankRegistry = new ConcurrentHashMap<>();
  private final Map<String, List<String>> phoneToBankIds =
    new ConcurrentHashMap<>();

  // --- Настройки вероятностей для случайных исходов ---
  private final int successOutcomeWeight = 85; // 85%
  private final int businessErrorOutcomeWeight = 10; // 10%

  // Техническая ошибка будет оставшимися 5% (100 - 85 - 10)

  @PostConstruct
  void initialize() {
    // Банки
    bankRegistry.put(
      "1000000001",
      new BankInfo("1000000001", "Alfa-Bank (Mock)", true)
    );
    bankRegistry.put(
      "1000000002",
      new BankInfo("1000000002", "SberBank (Mock)", true)
    );
    bankRegistry.put(
      "1000000003",
      new BankInfo("1000000003", "VTB (Mock)", true)
    );
    bankRegistry.put(
      "1000000004",
      new BankInfo("1000000004", "Tinkoff (Mock)", true)
    );
    bankRegistry.put(
      "1000000009",
      new BankInfo("1000000009", "Closed Bank (Mock)", false)
    );

    // Привязки телефонов к банкам
    phoneToBankIds.put("9991112222", List.of("1000000002"));
    phoneToBankIds.put("9993334444", List.of("1000000001", "1000000002"));
    phoneToBankIds.put("9995556666", List.of("1000000004"));
    phoneToBankIds.put("9997778888", List.of("1000000003", "1000000009"));
    phoneToBankIds.put("9990000009", List.of("1000000009"));
  }

  public BankInfo getBankById(String bankId) {
    return bankRegistry.get(bankId);
  }

  public List<String> getBankIdsByPhone(String phoneNumber) {
    return phoneToBankIds.get(phoneNumber);
  }
}
