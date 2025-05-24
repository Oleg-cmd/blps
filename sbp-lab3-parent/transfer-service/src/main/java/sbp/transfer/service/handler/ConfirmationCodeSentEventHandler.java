package sbp.transfer.service.handler;

import sbp.dto.jms.ConfirmationCodeSentEvent;

public interface ConfirmationCodeSentEventHandler {
  void handleEvent(ConfirmationCodeSentEvent event);
}
