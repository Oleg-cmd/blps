package sbp.dto.rest;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

  private Instant timestamp; // Время возникновения ошибки
  private int status; // HTTP статус код
  private String error; // Краткое описание ошибки (например, "Not Found", "Bad Request")
  private String message; // Более детальное сообщение об ошибке
  private String path; // Путь запроса, на котором произошла ошибка
}
