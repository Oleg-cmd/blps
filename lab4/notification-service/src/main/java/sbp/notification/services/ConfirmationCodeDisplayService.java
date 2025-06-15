package sbp.notification.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfirmationCodeDisplayService {
  void addCode(UUID correlationId, String phoneNumber, String code);

  void removeCode(UUID correlationId);

  Optional<String> getCode(UUID correlationId);

  List<DisplayedCode> getAllActiveCodes();

  // Внутренний класс или record для представления кода на UI
  record DisplayedCode(
    UUID correlationId,
    String phoneNumber,
    String code,
    long createdAt
  ) {}
}
