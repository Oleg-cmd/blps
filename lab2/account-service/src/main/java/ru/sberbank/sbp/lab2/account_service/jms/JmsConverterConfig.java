package ru.sberbank.sbp.lab2.account_service.jms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import ru.sberbank.sbp.lab2.account_service.dto.CompleteTransferCommand;
import ru.sberbank.sbp.lab2.account_service.dto.ReleaseFundsCommand;
import ru.sberbank.sbp.lab2.account_service.dto.ReserveFundsCommand;

@Configuration
public class JmsConverterConfig {

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  @Bean // Конвертер для JSON
  public MessageConverter jacksonJmsMessageConverter(
    ObjectMapper objectMapper
  ) {
    MappingJackson2MessageConverter converter =
      new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    converter.setObjectMapper(objectMapper);

    Map<String, Class<?>> typeIdMappings = new HashMap<>();
    // Маппинг для ReserveFundsCommand
    typeIdMappings.put(
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReserveFundsCommand",
      ReserveFundsCommand.class
    );
    // Маппинг для CompleteTransferCommand
    typeIdMappings.put(
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.CompleteTransferCommand",
      CompleteTransferCommand.class
    );
    // Маппинг для ReleaseFundsCommand
    typeIdMappings.put(
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReleaseFundsCommand",
      ReleaseFundsCommand.class
    );

    // Добавить маппинги для других команд, если нужно

    converter.setTypeIdMappings(typeIdMappings);

    return converter;
  }

  @Bean // Фабрика слушателей
  public JmsListenerContainerFactory<?> jmsListenerContainerFactory(
    ConnectionFactory connectionFactory,
    DefaultJmsListenerContainerFactoryConfigurer configurer,
    MessageConverter messageConverter,
    org.springframework.transaction.PlatformTransactionManager transactionManager
  ) {
    DefaultJmsListenerContainerFactory factory =
      new DefaultJmsListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setTransactionManager(transactionManager);
    factory.setSessionTransacted(true);
    return factory;
  }
}
