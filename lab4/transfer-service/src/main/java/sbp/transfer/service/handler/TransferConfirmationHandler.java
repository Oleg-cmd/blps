package sbp.transfer.service.handler;

import java.util.UUID;
import sbp.dto.rest.TransferConfirmationResponse;

public interface TransferConfirmationHandler {
  /**
   * Обрабатывает подтверждение перевода пользователем.
   * Проверяет код, обновляет статус, отправляет команду на списание средств.
   *
   * @param transferId        Уникальный идентификатор перевода (PK сущности Transfer).
   * @param userProvidedCode  Код подтверждения, введенный пользователем.
   * @param authenticatedUser Идентификатор аутентифицированного пользователя (например, номер телефона).
   * @return DTO с результатом операции подтверждения.
   */
  TransferConfirmationResponse confirmTransfer(
    UUID transferId,
    String userProvidedCode,
    String authenticatedUser
  );
}
