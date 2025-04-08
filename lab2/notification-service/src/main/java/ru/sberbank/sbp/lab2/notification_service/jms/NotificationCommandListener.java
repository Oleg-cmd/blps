package ru.sberbank.sbp.lab2.notification_service.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import ru.sberbank.sbp.lab2.notification_service.dto.SendConfirmationCodeCommand;
import ru.sberbank.sbp.lab2.notification_service.dto.SendFailureNotificationCommand;
import ru.sberbank.sbp.lab2.notification_service.dto.SendSuccessNotificationCommand;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCommandListener {

  @JmsListener(
    destination = JmsConfig.NOTIFICATION_SEND_CODE_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleSendCode(@Payload SendConfirmationCodeCommand command) {
    log.info("Received SendConfirmationCodeCommand: {}", command);
    // TODO: Имитация отправки кода
    log.info(
      "PRETENDING TO SEND CODE {} to {}",
      command.getCode(),
      command.getPhoneNumber()
    );
  }

  @JmsListener(
    destination = JmsConfig.NOTIFICATION_SEND_SUCCESS_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleSendSuccess(
    @Payload SendSuccessNotificationCommand command
  ) {
    log.info("Received SendSuccessNotificationCommand: {}", command);
    // TODO: Имитация отправки уведомления об успехе
    log.info(
      "PRETENDING TO SEND SUCCESS notification to {}",
      command.getSenderPhoneNumber()
    );
  }

  @JmsListener(
    destination = JmsConfig.NOTIFICATION_SEND_FAILURE_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void handleSendFailure(
    @Payload SendFailureNotificationCommand command
  ) {
    log.info("Received SendFailureNotificationCommand: {}", command);
    // TODO: Имитация отправки уведомления об ошибке
    log.info(
      "PRETENDING TO SEND FAILURE notification to {} (reason: {})",
      command.getSenderPhoneNumber(),
      command.getReason()
    );
  }
}
