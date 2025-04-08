package ru.sberbank.sbp.lab2.transfer_service.integration;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

  @Bean
  @Qualifier("sbpAdapterRestTemplate")
  public RestTemplate sbpAdapterRestTemplate(RestTemplateBuilder builder) {
    return builder
      .setConnectTimeout(Duration.ofSeconds(3)) // Таймаут соединения
      .setReadTimeout(Duration.ofSeconds(10)) // Таймаут чтения ответа
      .build();
  }
}
