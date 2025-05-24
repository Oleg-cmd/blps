package sbp.transfer.service.handler;

import sbp.dto.jms.FundsProcessedEvent;

public interface FundsProcessedEventHandler {
  void handleEvent(FundsProcessedEvent event);
}
