package ru.sberbank.sbp.lab2.notification_service.jms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;
// Импорт ActiveMQConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory;
// Импорт Value для чтения properties
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
// Импорты ЛОКАЛЬНЫХ DTO
import ru.sberbank.sbp.lab2.notification_service.dto.SendConfirmationCodeCommand;
import ru.sberbank.sbp.lab2.notification_service.dto.SendFailureNotificationCommand;
import ru.sberbank.sbp.lab2.notification_service.dto.SendSuccessNotificationCommand;

@Configuration
public class JmsConverterConfig {

  @Value("${spring.activemq.broker-url:tcp://localhost:61616}")
  private String brokerUrl;

  @Value("${spring.activemq.user:#{null}}")
  private String user;

  @Value("${spring.activemq.password:#{null}}")
  private String password;

  // Создаем обычную (не XA) ConnectionFactory
  @Bean
  public ConnectionFactory connectionFactory() {
    ActiveMQConnectionFactory activeMQConnectionFactory =
      new ActiveMQConnectionFactory();
    activeMQConnectionFactory.setBrokerURL(brokerUrl);
    if (user != null && !user.isEmpty() && password != null) {
      activeMQConnectionFactory.setUserName(user);
      activeMQConnectionFactory.setPassword(password);
    }
    // Важно для десериализации DTO из другого пакета (если spring.activemq.packages.trust-all=false)
    // activeMQConnectionFactory.setTrustedPackages(Arrays.asList("ru.sberbank.sbp.lab2.transfer_service.dto.jms", "java.util", "java.lang", "java.math"));
    activeMQConnectionFactory.setTrustAllPackages(true); // Проще для ЛР
    return activeMQConnectionFactory;
    // Можно обернуть в CachingConnectionFactory для производительности,
    // но Spring Boot с включенным пулом (spring.activemq.pool.enabled=true) делает это сам.
    // CachingConnectionFactory cachingFactory = new CachingConnectionFactory(activeMQConnectionFactory);
    // return cachingFactory;
  }

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  @Bean
  public MessageConverter jacksonJmsMessageConverter(
    ObjectMapper objectMapper
  ) {
    MappingJackson2MessageConverter converter =
      new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    converter.setObjectMapper(objectMapper);

    Map<String, Class<?>> typeIdMappings = new HashMap<>();
    typeIdMappings.put(
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendConfirmationCodeCommand",
      SendConfirmationCodeCommand.class
    );
    typeIdMappings.put(
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendSuccessNotificationCommand",
      SendSuccessNotificationCommand.class
    );
    typeIdMappings.put(
      "ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendFailureNotificationCommand",
      SendFailureNotificationCommand.class
    );
    converter.setTypeIdMappings(typeIdMappings);

    return converter;
  }

  // Фабрика для слушателей (НЕ JTA)
  @Bean
  public JmsListenerContainerFactory<?> jmsListenerContainerFactory(
    ConnectionFactory connectionFactory,
    DefaultJmsListenerContainerFactoryConfigurer configurer,
    MessageConverter messageConverter
  ) {
    DefaultJmsListenerContainerFactory factory =
      new DefaultJmsListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setMessageConverter(messageConverter);
    return factory;
  }
}
