package sbp.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Queue;
import java.util.HashMap;
import java.util.Map;
import org.apache.activemq.command.ActiveMQQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import sbp.dto.JmsQueueNames;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.dto.jms.SendConfirmationCodeCommand;
import sbp.dto.jms.SendFailureNotificationCommand;
import sbp.dto.jms.SendSuccessNotificationCommand;

@Configuration
@EnableJms
public class JmsConfig {

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  @Bean
  public MessageConverter jacksonJmsMessageConverter(
    @Qualifier("objectMapper") ObjectMapper objectMapper
  ) {
    MappingJackson2MessageConverter converter =
      new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    converter.setObjectMapper(objectMapper);

    Map<String, Class<?>> typeIdMappings = new HashMap<>();
    typeIdMappings.put(
      SendConfirmationCodeCommand.class.getName(),
      SendConfirmationCodeCommand.class
    );
    typeIdMappings.put(
      SendSuccessNotificationCommand.class.getName(),
      SendSuccessNotificationCommand.class
    );
    typeIdMappings.put(
      SendFailureNotificationCommand.class.getName(),
      SendFailureNotificationCommand.class
    );
    typeIdMappings.put(
      ConfirmationCodeSentEvent.class.getName(),
      ConfirmationCodeSentEvent.class
    );
    converter.setTypeIdMappings(typeIdMappings);
    return converter;
  }

  @Bean
  public JmsTemplate jmsTemplate(
    ConnectionFactory connectionFactory,
    MessageConverter messageConverter
  ) {
    JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
    jmsTemplate.setMessageConverter(messageConverter);
    jmsTemplate.setSessionTransacted(true);
    return jmsTemplate;
  }

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
    factory.setSessionTransacted(true);
    return factory;
  }

  // Явное объявление бинов очередей
  @Bean
  public Queue notificationSendCodeCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.NOTIFICATION_SEND_CODE_CMD_QUEUE);
  }

  @Bean
  public Queue notificationSendSuccessCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.NOTIFICATION_SEND_SUCCESS_CMD_QUEUE);
  }

  @Bean
  public Queue notificationSendFailureCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.NOTIFICATION_SEND_FAILURE_CMD_QUEUE);
  }

  // Очередь, в которую этот сервис ОТПРАВЛЯЕТ сообщения
  @Bean
  public Queue transferConfirmationCodeSentEventQueue() {
    return new ActiveMQQueue(
      JmsQueueNames.TRANSFER_CONFIRMATION_CODE_SENT_EVENT_QUEUE
    );
  }
}
