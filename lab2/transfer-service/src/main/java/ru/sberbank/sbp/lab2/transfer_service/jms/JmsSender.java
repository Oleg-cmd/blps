package ru.sberbank.sbp.lab2.transfer_service.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.CompleteTransferCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReleaseFundsCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.ReserveFundsCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendConfirmationCodeCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendFailureNotificationCommand;
import ru.sberbank.sbp.lab2.transfer_service.dto.jms.SendSuccessNotificationCommand;

@Component
@RequiredArgsConstructor
@Slf4j
public class JmsSender {

  private final JmsTemplate jmsTemplate;

  public void sendReserveFundsCommand(ReserveFundsCommand command) {
    try {
      log.info(
        "Sending command to queue [{}]: CorrelationId: {}",
        JmsConfig.ACCOUNT_RESERVE_FUNDS_QUEUE,
        command.getCorrelationId()
      );
      jmsTemplate.convertAndSend(
        JmsConfig.ACCOUNT_RESERVE_FUNDS_QUEUE,
        command
      );
      log.debug(
        "ReserveFundsCommand with CorrelationId [{}] successfully sent.",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      handleSendError(
        JmsConfig.ACCOUNT_RESERVE_FUNDS_QUEUE,
        command.getCorrelationId(),
        e
      );
    }
  }

  public void sendCompleteTransferCommand(CompleteTransferCommand command) {
    try {
      log.info(
        "Sending command to queue [{}]: CorrelationId: {}",
        JmsConfig.ACCOUNT_COMPLETE_TRANSFER_QUEUE,
        command.getCorrelationId()
      );
      jmsTemplate.convertAndSend(
        JmsConfig.ACCOUNT_COMPLETE_TRANSFER_QUEUE,
        command
      );
      log.debug(
        "CompleteTransferCommand with CorrelationId [{}] successfully sent.",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      handleSendError(
        JmsConfig.ACCOUNT_COMPLETE_TRANSFER_QUEUE,
        command.getCorrelationId(),
        e
      );
    }
  }

  public void sendReleaseFundsCommand(ReleaseFundsCommand command) {
    try {
      log.info(
        "Sending command to queue [{}]: CorrelationId: {}",
        JmsConfig.ACCOUNT_RELEASE_FUNDS_QUEUE,
        command.getCorrelationId()
      );
      jmsTemplate.convertAndSend(
        JmsConfig.ACCOUNT_RELEASE_FUNDS_QUEUE,
        command
      );
      log.debug(
        "ReleaseFundsCommand with CorrelationId [{}] successfully sent.",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      handleSendError(
        JmsConfig.ACCOUNT_RELEASE_FUNDS_QUEUE,
        command.getCorrelationId(),
        e
      );
    }
  }

  // --- Методы для Notification Service ---
  public void sendConfirmationCodeCommand(SendConfirmationCodeCommand command) {
    // Отправка уведомлений может быть менее критичной,
    // поэтому можно не бросать RuntimeException при ошибке, а только логировать.
    try {
      log.info(
        "Sending command to queue [{}]: CorrelationId: {}",
        JmsConfig.NOTIFICATION_SEND_CODE_QUEUE,
        command.getCorrelationId()
      );
      jmsTemplate.convertAndSend(
        JmsConfig.NOTIFICATION_SEND_CODE_QUEUE,
        command
      );
      log.debug(
        "SendConfirmationCodeCommand with CorrelationId [{}] successfully sent.",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      handleNotificationSendError(
        JmsConfig.NOTIFICATION_SEND_CODE_QUEUE,
        command.getCorrelationId(),
        e
      );
    }
  }

  public void sendSuccessNotificationCommand(
    SendSuccessNotificationCommand command
  ) {
    try {
      log.info(
        "Sending command to queue [{}]: CorrelationId: {}",
        JmsConfig.NOTIFICATION_SEND_SUCCESS_QUEUE,
        command.getCorrelationId()
      );
      jmsTemplate.convertAndSend(
        JmsConfig.NOTIFICATION_SEND_SUCCESS_QUEUE,
        command
      );
      log.debug(
        "SendSuccessNotificationCommand with CorrelationId [{}] successfully sent.",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      handleNotificationSendError(
        JmsConfig.NOTIFICATION_SEND_SUCCESS_QUEUE,
        command.getCorrelationId(),
        e
      );
    }
  }

  public void sendFailureNotificationCommand(
    SendFailureNotificationCommand command
  ) {
    try {
      log.info(
        "Sending command to queue [{}]: CorrelationId: {}",
        JmsConfig.NOTIFICATION_SEND_FAILURE_QUEUE,
        command.getCorrelationId()
      );
      jmsTemplate.convertAndSend(
        JmsConfig.NOTIFICATION_SEND_FAILURE_QUEUE,
        command
      );
      log.debug(
        "SendFailureNotificationCommand with CorrelationId [{}] successfully sent.",
        command.getCorrelationId()
      );
    } catch (Exception e) {
      handleNotificationSendError(
        JmsConfig.NOTIFICATION_SEND_FAILURE_QUEUE,
        command.getCorrelationId(),
        e
      );
    }
  }

  // Общий обработчик ошибок для команд уведомлений (можно просто логировать)
  private void handleNotificationSendError(
    String queueName,
    Object correlationId,
    Exception e
  ) {
    log.error(
      "Error sending notification command with CorrelationId [{}] to queue [{}]: {}",
      correlationId,
      queueName,
      e.getMessage(),
      e
    );
    // Не бросаем RuntimeException, чтобы не откатывать основную транзакцию перевода
    // throw new RuntimeException(...)
  }

  // Вспомогательный метод для обработки ошибок
  private void handleSendError(
    String queueName,
    Object correlationId,
    Exception e
  ) {
    log.error(
      "Error sending command with CorrelationId [{}] to queue [{}]: {}",
      correlationId,
      queueName,
      e.getMessage(),
      e
    );
    // Важно откатить транзакцию
    throw new RuntimeException(
      "Failed to send JMS command for CorrelationId: " +
      correlationId +
      " to queue " +
      queueName,
      e
    );
  }
}
