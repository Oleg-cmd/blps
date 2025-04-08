package ru.sberbank.sbp.lab2.transfer_service.jms;

public final class JmsConfig {

  private JmsConfig() {}

  // --- Account Service Queues ---
  public static final String ACCOUNT_RESERVE_FUNDS_QUEUE =
    "account.command.reserve.queue";
  public static final String ACCOUNT_COMPLETE_TRANSFER_QUEUE =
    "account.command.complete.queue";
  public static final String ACCOUNT_RELEASE_FUNDS_QUEUE =
    "account.command.release.queue";

  // --- Notification Service Queues ---
  public static final String NOTIFICATION_SEND_CODE_QUEUE =
    "notification.command.sendcode.queue";
  public static final String NOTIFICATION_SEND_SUCCESS_QUEUE =
    "notification.command.sendsuccess.queue";
  public static final String NOTIFICATION_SEND_FAILURE_QUEUE =
    "notification.command.sendfailure.queue";
  // TODO: Добавить очереди для SBP Adapter, если нужно
}
