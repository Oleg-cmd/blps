package sbp.transfer.service.handler.impl; // Реализации хендлеров

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.transfer.entity.Transfer;
import sbp.transfer.repository.TransferRepository;
import sbp.transfer.service.handler.ConfirmationCodeSentEventHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmationCodeSentEventHandlerImpl
  implements ConfirmationCodeSentEventHandler {

  private final TransferRepository transferRepository;

  @Override
  @Transactional(Transactional.TxType.REQUIRED)
  public void handleEvent(ConfirmationCodeSentEvent event) {
    log.info(
      "[{}] Handling ConfirmationCodeSentEvent.",
      event.getCorrelationId()
    );
    Transfer transfer = transferRepository
      .findByCorrelationId(event.getCorrelationId())
      .orElse(null);

    if (transfer == null) {
      log.warn(
        "[{}] Transfer not found for ConfirmationCodeSentEvent. Event might be outdated or transfer was cancelled.",
        event.getCorrelationId()
      );
      return;
    }

    log.debug(
      "[{}] Transfer (DB ID: {}) found for ConfirmationCodeSentEvent. Current status: {}",
      transfer.getCorrelationId(),
      transfer.getId(),
      transfer.getStatus()
    );

    log.info(
      "[{}] Logged ConfirmationCodeSentEvent for transfer (DB ID: {}). No specific state change applied.",
      transfer.getCorrelationId(),
      transfer.getId()
    );
  }
}
