package ru.sberbank.sbp.lab2.transfer_service.jms;

// Добавляем импорты
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@Configuration
public class JmsSenderConfig {

  @Bean // Конвертер для JSON (в transfer-service)
  public MessageConverter jacksonJmsMessageConverter(
    ObjectMapper objectMapper
  ) { // Тоже используем настроенный ObjectMapper
    MappingJackson2MessageConverter converter =
      new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    converter.setObjectMapper(objectMapper);
    return converter;
  }

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }
  // JmsTemplate будет авто-сконфигурирован Spring Boot + Atomikos
  // ... остальной код без изменений ...
}
