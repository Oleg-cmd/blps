package ru.sberbank.sbp.lab2.account_service.jms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
// Добавляем импорты для Map
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
// Импортируем ЛОКАЛЬНЫЙ класс DTO
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

    // !!! Настройка маппинга Type ID на локальный класс !!!
    Map<String, Class<?>> typeIdMappings = new HashMap<>();
    // Ключ: Полное имя класса, которое приходит в _type из transfer-service
    // Значение: Локальный класс в account-service, в который нужно десериализовать
    typeIdMappings.put(
      // Обрати внимание на имя пакета - он из transfer-service
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReserveFundsCommand",
      // Используем .class для получения локального класса
      ReserveFundsCommand.class
    );
    // Если будут другие команды, добавь их маппинги сюда
    // typeIdMappings.put("ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReleaseFundsCommand",
    //                    ru.sberbank.sbp.lab2.account_service.dto.ReleaseFundsCommand.class);

    // Устанавливаем настроенный маппинг в конвертер
    converter.setTypeIdMappings(typeIdMappings);

    return converter;
  }

  @Bean // Фабрика слушателей
  public JmsListenerContainerFactory<?> jmsListenerContainerFactory(
    ConnectionFactory connectionFactory,
    DefaultJmsListenerContainerFactoryConfigurer configurer,
    MessageConverter messageConverter, // Внедряем наш настроенный конвертер с маппингом
    org.springframework.transaction.PlatformTransactionManager transactionManager
  ) {
    DefaultJmsListenerContainerFactory factory =
      new DefaultJmsListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setMessageConverter(messageConverter); // Используем конвертер с маппингом
    factory.setTransactionManager(transactionManager);
    factory.setSessionTransacted(true);
    return factory;
  }
}
