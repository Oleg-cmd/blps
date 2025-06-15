package sbp.transfer.jms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import sbp.dto.JmsQueueNames;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.dto.jms.FundsProcessedEvent;
import sbp.transfer.service.TransferOrchestrationService;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventListener {

  private final TransferOrchestrationService transferOrchestrationService;

  @JmsListener(
    destination = JmsQueueNames.TRANSFER_PROCESS_EIS_CMD_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void onFundsProcessed(@Payload FundsProcessedEvent event) {
    log.info(
      "Listener received FundsProcessedEvent for correlationId: {}",
      event.getCorrelationId()
    );
    try {
      transferOrchestrationService.handleAccountFundsProcessedResponse(event);
      log.info(
        "Listener successfully processed FundsProcessedEvent for correlationId: {}",
        event.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "Listener error processing FundsProcessedEvent for correlationId: {}. Reason: {}",
        event.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Listener error processing FundsProcessedEvent: " + e.getMessage(),
        e
      );
    }
  }

  @JmsListener(
    destination = JmsQueueNames.TRANSFER_CONFIRMATION_CODE_SENT_EVENT_QUEUE,
    containerFactory = "jmsListenerContainerFactory"
  )
  public void onConfirmationCodeSent(@Payload ConfirmationCodeSentEvent event) {
    log.info(
      "Listener received ConfirmationCodeSentEvent for correlationId: {}",
      event.getCorrelationId()
    );
    try {
      transferOrchestrationService.handleConfirmationCodeSent(event);
      log.info(
        "Listener successfully processed ConfirmationCodeSentEvent for correlationId: {}",
        event.getCorrelationId()
      );
    } catch (Exception e) {
      log.error(
        "Listener error processing ConfirmationCodeSentEvent for correlationId: {}. Reason: {}",
        event.getCorrelationId(),
        e.getMessage(),
        e
      );
      throw new RuntimeException(
        "Listener error processing ConfirmationCodeSentEvent: " +
        e.getMessage(),
        e
      );
    }
  }
}
