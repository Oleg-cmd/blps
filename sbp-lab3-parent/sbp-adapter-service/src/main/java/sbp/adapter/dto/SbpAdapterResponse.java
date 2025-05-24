package sbp.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SbpAdapterResponse {

  private boolean success;
  private String sbpTransactionId;
  private String errorMessage;
}
