package sbp.notification.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import sbp.dto.JmsQueueNames;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.dto.jms.SendConfirmationCodeCommand;
import sbp.dto.jms.SendFailureNotificationCommand;
import sbp.dto.jms.SendSuccessNotificationCommand;
import sbp.notification.services.ConfirmationCodeDisplayService;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCommandListener {

  private final JmsTemplate jmsTemplate;
  private final ConfirmationCodeDisplayService confirmationCodeDisplayService;

  @JmsListener(destination = JmsQueueNames.NOTIFICATION_SEND_CODE_CMD_QUEUE)
  public void handleSendConfirmationCode(
    @Payload SendConfirmationCodeCommand command
  ) {
    log.debug(
      "Processing SendConfirmationCodeCommand for CorrelationId: {}",
      command.getCorrelationId()
    );

    // "Отправляем" код
    log.info(
      "SENDING code {} to phone {} for CorrelationId: {}",
      command.getCode(),
      command.getPhoneNumber(),
      command.getCorrelationId()
    );

    confirmationCodeDisplayService.addCode(
      command.getCorrelationId(),
      command.getPhoneNumber(),
      command.getCode()
    );

    ConfirmationCodeSentEvent event = ConfirmationCodeSentEvent.builder()
      .correlationId(command.getCorrelationId())
      .build();
    try {
      jmsTemplate.convertAndSend(
        JmsQueueNames.TRANSFER_CONFIRMATION_CODE_SENT_EVENT_QUEUE,
        event
      );
      log.debug(
        "Sent ConfirmationCodeSentEvent for CorrelationId: {}",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "Failed to send ConfirmationCodeSentEvent for CorrelationId: {}. Error: {}",
        command.getCorrelationId(),
        e.getMessage()
      );
    }
  }

  @JmsListener(destination = JmsQueueNames.NOTIFICATION_SEND_SUCCESS_CMD_QUEUE)
  public void handleSendSuccessNotification(
    @Payload SendSuccessNotificationCommand command
  ) {
    log.debug(
      "Processing SendSuccessNotificationCommand for CorrelationId: {}",
      command.getCorrelationId()
    );
    log.info(
      "SIMULATING SENDING SUCCESS NOTIFICATION for CorrelationId: {}",
      command.getCorrelationId()
    );
    confirmationCodeDisplayService.removeCode(command.getCorrelationId());
  }

  @JmsListener(destination = JmsQueueNames.NOTIFICATION_SEND_FAILURE_CMD_QUEUE)
  public void handleSendFailureNotification(
    @Payload SendFailureNotificationCommand command
  ) {
    log.debug(
      "Processing SendFailureNotificationCommand for CorrelationId: {}",
      command.getCorrelationId()
    );
    log.info(
      "SIMULATING SENDING FAILURE NOTIFICATION for CorrelationId: {}, Reason: {}",
      command.getCorrelationId(),
      command.getReason()
    );
    confirmationCodeDisplayService.removeCode(command.getCorrelationId());
  }
}
