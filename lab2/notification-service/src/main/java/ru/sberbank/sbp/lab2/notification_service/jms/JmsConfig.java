package ru.sberbank.sbp.lab2.notification_service.jms;

public final class JmsConfig {

  private JmsConfig() {}

  // Очереди, которые СЛУШАЕТ этот сервис
  public static final String NOTIFICATION_SEND_CODE_QUEUE =
    "notification.command.sendcode.queue";
  public static final String NOTIFICATION_SEND_SUCCESS_QUEUE =
    "notification.command.sendsuccess.queue";
  public static final String NOTIFICATION_SEND_FAILURE_QUEUE =
    "notification.command.sendfailure.queue";
  // Имена очередей account service здесь не нужны
}
