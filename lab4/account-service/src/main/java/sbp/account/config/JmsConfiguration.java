package sbp.account.config;

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
import sbp.dto.jms.CompleteTransferCommand;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.dto.jms.FundsProcessedEvent;
import sbp.dto.jms.ReleaseFundsCommand;
import sbp.dto.jms.ReserveFundsCommand;
import sbp.dto.jms.SendConfirmationCodeCommand;

@Configuration
@EnableJms
public class JmsConfiguration {

  @Bean
  public ObjectMapper objectMapperForJms() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  @Bean
  public MessageConverter jacksonJmsMessageConverter(
    @Qualifier("objectMapperForJms") ObjectMapper objectMapper
  ) {
    MappingJackson2MessageConverter converter =
      new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    converter.setObjectMapper(objectMapper);

    Map<String, Class<?>> typeIdMappings = new HashMap<>();
    typeIdMappings.put(
      ReserveFundsCommand.class.getName(),
      ReserveFundsCommand.class
    );
    typeIdMappings.put(
      ReleaseFundsCommand.class.getName(),
      ReleaseFundsCommand.class
    );

    typeIdMappings.put(
      SendConfirmationCodeCommand.class.getName(),
      SendConfirmationCodeCommand.class
    );
    typeIdMappings.put(
      ConfirmationCodeSentEvent.class.getName(),
      ConfirmationCodeSentEvent.class
    );
    typeIdMappings.put(
      FundsProcessedEvent.class.getName(),
      FundsProcessedEvent.class
    );
    typeIdMappings.put(
      CompleteTransferCommand.class.getName(),
      CompleteTransferCommand.class
    );
    converter.setTypeIdMappings(typeIdMappings);

    return converter;
  }

  @Bean
  public JmsTemplate jmsTemplate(
    ConnectionFactory connectionFactory,
    MessageConverter messageConverter
  ) {
    JmsTemplate template = new JmsTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);
    template.setSessionTransacted(true);
    return template;
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

  @Bean
  public Queue accountReserveFundsCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.ACCOUNT_RESERVE_FUNDS_CMD_QUEUE);
  }

  @Bean
  public Queue accountCompleteTransferCmdQueue() {
    return new ActiveMQQueue(
      JmsQueueNames.ACCOUNT_TRANSFER_COMPLETED_EVENT_QUEUE
    );
  }

  @Bean
  public Queue accountReleaseFundsCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.ACCOUNT_RELEASE_FUNDS_CMD_QUEUE);
  }

  // Очереди, используемые для ОТПРАВКИ из account-service
  @Bean
  public Queue notificationSendCodeCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.NOTIFICATION_SEND_CODE_CMD_QUEUE);
  }

  @Bean
  public Queue transferProcessEisCmdQueue() {
    return new ActiveMQQueue(JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE);
  }

  @Bean
  public Queue transferConfirmationCodeSentEventQueue() {
    return new ActiveMQQueue(
      JmsQueueNames.TRANSFER_CONFIRMATION_CODE_SENT_EVENT_QUEUE
    );
  }
}
