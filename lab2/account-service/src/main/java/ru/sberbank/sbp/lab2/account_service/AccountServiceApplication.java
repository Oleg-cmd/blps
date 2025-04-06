package ru.sberbank.sbp.lab2.account_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Импортируем классы автоконфигураций для исключения
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration; // Добавляем это
// JmsAutoConfiguration пока не трогаем
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication(
  exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class, // Отключаем автоконфигурацию инициализации SQL
  }
)
@EnableJms
public class AccountServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AccountServiceApplication.class, args);
  }
}
