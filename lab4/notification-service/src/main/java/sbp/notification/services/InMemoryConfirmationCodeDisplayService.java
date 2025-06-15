package sbp.notification.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InMemoryConfirmationCodeDisplayService
  implements ConfirmationCodeDisplayService {

  // Храним коды в потокобезопасной мапе: CorrelationID -> DisplayedCode
  private final Map<UUID, DisplayedCode> activeCodes =
    new ConcurrentHashMap<>();
  private static final long CODE_DISPLAY_TTL_MS = 5 * 60 * 1000; // 5 минут для отображения

  @Override
  public void addCode(UUID correlationId, String phoneNumber, String code) {
    // Удаляем старый код с тем же correlationId, если он был, чтобы избежать дублирования
    // или если это повторная отправка кода для той же операции.
    activeCodes.remove(correlationId);
    activeCodes.put(
      correlationId,
      new DisplayedCode(
        correlationId,
        phoneNumber,
        code,
        System.currentTimeMillis()
      )
    );
  }

  @Override
  public void removeCode(UUID correlationId) {
    activeCodes.remove(correlationId);
  }

  @Override
  public Optional<String> getCode(UUID correlationId) {
    cleanupExpiredCodes();
    return Optional.ofNullable(activeCodes.get(correlationId)).map(
      DisplayedCode::code
    );
  }

  @Override
  public List<DisplayedCode> getAllActiveCodes() {
    cleanupExpiredCodes();
    return activeCodes
      .values()
      .stream()
      .sorted((c1, c2) -> Long.compare(c2.createdAt(), c1.createdAt())) // Сначала новые
      .collect(Collectors.toList());
  }

  private void cleanupExpiredCodes() {
    long now = System.currentTimeMillis();
    activeCodes
      .entrySet()
      .removeIf(
        entry -> (now - entry.getValue().createdAt()) > CODE_DISPLAY_TTL_MS
      );
  }
}
