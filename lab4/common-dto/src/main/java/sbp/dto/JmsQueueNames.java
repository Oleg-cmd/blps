package sbp.dto;

public final class JmsQueueNames {

  private JmsQueueNames() {
    // Utility class, non-instantiable
  }

  // Commands from TransferService to AccountService
  public static final String ACCOUNT_RESERVE_FUNDS_CMD_QUEUE =
    "q.account.cmd.fund.reserve";
  public static final String ACCOUNT_RELEASE_FUNDS_CMD_QUEUE =
    "q.account.cmd.fund.release";
  public static final String ACCOUNT_TRANSFER_COMPLETED_EVENT_QUEUE =
    "q.account.cmd.fund.success";

  // Commands from AccountService to NotificationService
  public static final String NOTIFICATION_SEND_CODE_CMD_QUEUE =
    "q.notification.cmd.code.send";

  // Events from NotificationService to TransferService
  public static final String TRANSFER_CONFIRMATION_CODE_SENT_EVENT_QUEUE =
    "q.transfer.event.code.sent";

  // Commands from AccountService to TransferService (после успешного дебета/кредита и перед EIS)
  public static final String TRANSFER_PROCESS_EIS_CMD_QUEUE =
    "q.transfer.cmd.process.eis";

  // Commands from TransferService to NotificationService (для финальных уведомлений)
  public static final String NOTIFICATION_SEND_SUCCESS_CMD_QUEUE =
    "q.notification.cmd.transfer.success";
  public static final String NOTIFICATION_SEND_FAILURE_CMD_QUEUE =
    "q.notification.cmd.transfer.failure";

  public static final String DLQ_PREFIX = "dlq."; // Префикс для Dead Letter Queues
}
