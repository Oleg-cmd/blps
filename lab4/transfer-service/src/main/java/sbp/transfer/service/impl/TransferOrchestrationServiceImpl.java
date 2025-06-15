package sbp.transfer.service.impl; // Или sbp.transfer.service.orchestration.impl

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.dto.jms.FundsProcessedEvent;
import sbp.dto.rest.InitiateTransferRequest;
import sbp.dto.rest.TransferConfirmationResponse;
import sbp.dto.rest.TransferInitiationResponse;
import sbp.transfer.service.TransferOrchestrationService;
import sbp.transfer.service.handler.ConfirmationCodeSentEventHandler;
import sbp.transfer.service.handler.FundsProcessedEventHandler;
import sbp.transfer.service.handler.TransferConfirmationHandler;
import sbp.transfer.service.handler.TransferInitiationHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferOrchestrationServiceImpl
  implements TransferOrchestrationService {

  private final TransferInitiationHandler transferInitiationHandler;
  private final TransferConfirmationHandler transferConfirmationHandler;
  private final FundsProcessedEventHandler fundsProcessedEventHandler;
  private final ConfirmationCodeSentEventHandler confirmationCodeSentEventHandler;

  @Override
  public TransferInitiationResponse initiateTransfer(
    UUID correlationId,
    String senderPhoneNumber,
    InitiateTransferRequest request
  ) {
    return transferInitiationHandler.initiateTransfer(
      correlationId,
      senderPhoneNumber,
      request
    );
  }

  @Override
  public TransferConfirmationResponse confirmTransfer(
    UUID transferId,
    String userProvidedCode,
    String authenticatedUser
  ) {
    return transferConfirmationHandler.confirmTransfer(
      transferId,
      userProvidedCode,
      authenticatedUser
    );
  }

  @Override
  public void handleAccountFundsProcessedResponse(FundsProcessedEvent event) {
    fundsProcessedEventHandler.handleEvent(event);
  }

  @Override
  public void handleConfirmationCodeSent(ConfirmationCodeSentEvent event) {
    confirmationCodeSentEventHandler.handleEvent(event);
  }
}
