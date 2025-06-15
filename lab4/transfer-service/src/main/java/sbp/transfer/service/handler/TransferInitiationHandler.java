package sbp.transfer.service.handler;

import java.util.UUID;
import sbp.dto.rest.InitiateTransferRequest;
import sbp.dto.rest.TransferInitiationResponse;

public interface TransferInitiationHandler {
  /**
   * Инициирует процесс перевода средств.
   * Создает запись о переводе, генерирует код подтверждения,
   * отправляет команду на резервирование средств в AccountService.
   *
   * @param correlationId     Уникальный идентификатор для сквозного отслеживания операции.
   * @param senderPhoneNumber Номер телефона отправителя (аутентифицированного пользователя).
   * @param request           DTO с деталями запроса на перевод.
   * @return                  DTO с начальной информацией о созданном переводе.
   */
  TransferInitiationResponse initiateTransfer(
    UUID correlationId,
    String senderPhoneNumber,
    InitiateTransferRequest request
  );
}
