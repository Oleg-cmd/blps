package ru.sberbank.sbp.lab2.account_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts") // Явно укажем имя таблицы
@Data // Lombok: генерирует геттеры, сеттеры, toString, equals, hashCode
@NoArgsConstructor // Lombok: конструктор без аргументов
@AllArgsConstructor // Lombok: конструктор со всеми аргументами
@Builder // Lombok: Builder pattern
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY) // Используем автоинкремент БД
  private Long id;

  @NotBlank(message = "Phone number is mandatory")
  @Pattern(regexp = "\\d{10}", message = "Phone number must be 10 digits")
  @Column(nullable = false, unique = true, length = 10) // Номер телефона - уникальный ключ
  private String phoneNumber;

  @NotNull(message = "Balance cannot be null")
  @DecimalMin(value = "0.00", message = "Balance cannot be negative")
  @Column(nullable = false, precision = 19, scale = 2) // Тип для денег
  private BigDecimal balance;

  @NotNull(message = "Reserved amount cannot be null")
  @DecimalMin(value = "0.00", message = "Reserved amount cannot be negative")
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal reservedAmount;

  @Version // Для оптимистичной блокировки
  private Long version;

  // Инициализация полей по умолчанию в конструкторе без аргументов,
  // если нужно (Lombok @Builder.Default можно использовать, но так проще)
  public Account(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    this.balance = BigDecimal.ZERO;
    this.reservedAmount = BigDecimal.ZERO;
  }
}
