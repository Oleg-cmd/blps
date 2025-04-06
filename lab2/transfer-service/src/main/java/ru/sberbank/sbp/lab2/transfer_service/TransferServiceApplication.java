package ru.sberbank.sbp.lab2.transfer_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Импортируем классы автоконфигураций для исключения
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
// JmsAutoConfiguration пока не трогаем, так как она нужна для JmsTemplate
import org.springframework.jms.annotation.EnableJms; // EnableJms здесь не нужен, т.к. нет слушателей

@SpringBootApplication(
  exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class,
  }
)
// @EnableJms // Можно убрать, если в этом сервисе нет @JmsListener
public class TransferServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TransferServiceApplication.class, args);
  }
}
