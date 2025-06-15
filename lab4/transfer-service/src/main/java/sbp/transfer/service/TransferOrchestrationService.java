package sbp.transfer.service;

import java.util.UUID;
import sbp.dto.jms.ConfirmationCodeSentEvent;
import sbp.dto.jms.FundsProcessedEvent; // Это будет использоваться для обоих этапов ответа от AccountService
import sbp.dto.rest.InitiateTransferRequest;
import sbp.dto.rest.TransferConfirmationResponse;
import sbp.dto.rest.TransferInitiationResponse;

public interface TransferOrchestrationService {
  /**
   * Инициирует процесс перевода средств.
   */
  TransferInitiationResponse initiateTransfer(
    UUID correlationId,
    String senderPhoneNumber,
    InitiateTransferRequest request
  );

  /**
   * Обрабатывает подтверждение перевода пользователем.
   */
  TransferConfirmationResponse confirmTransfer(
    UUID transferId,
    String userProvidedCode,
    String authenticatedUser
  );

  /**
   * Обрабатывает событие FundsProcessedEvent от AccountService.
   * Этот метод будет вызван после этапа резервирования И после этапа финального списания/зачисления.
   * Внутренняя логика должна определить, какой это этап, по текущему статусу Transfer'а.
   *
   * @param event DTO события от AccountService.
   */
  void handleAccountFundsProcessedResponse(FundsProcessedEvent event);

  /**
   * Обрабатывает событие об отправке кода подтверждения от NotificationService.
   * Может использоваться для обновления статуса или логики таймаутов.
   *
   * @param event DTO события от NotificationService.
   */
  void handleConfirmationCodeSent(ConfirmationCodeSentEvent event);
  // Возможные будущие методы:
  // void processEisCheque(UUID correlationId); // Если AccountService шлет отдельную команду для EIS
}
