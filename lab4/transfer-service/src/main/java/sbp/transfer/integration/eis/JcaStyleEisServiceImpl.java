package sbp.transfer.integration.eis;

import jakarta.annotation.Resource; // Для инъекции по имени
import jakarta.resource.cci.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbp.dto.EisService;
import sbp.dto.eis.ChequeDetailsDTO;
import sbp.transfer.jca.email.EmailConnection;

@Service("jcaStyleEisService") // Имя бина
@Slf4j
public class JcaStyleEisServiceImpl implements EisService {

  // Инжектируем нашу CCI ConnectionFactory по имени, которое мы задали в EmailConnectorConfig
  @Resource(name = "emailConnectionFactory")
  private ConnectionFactory cciConnectionFactory;

  @Override
  public void sendElectronicCheque(ChequeDetailsDTO chequeDetails)
    throws Exception {
    log.info(
      "JCA-Style EIS: Attempting to send electronic cheque for transaction ID: {}",
      chequeDetails.getTransactionId()
    );
    EmailConnection emailConnection = null;
    try {
      // Получаем наше кастомное EmailConnection
      emailConnection = (EmailConnection) cciConnectionFactory.getConnection();
      emailConnection.sendElectronicCheque(chequeDetails);
      log.info(
        "JCA-Style EIS: Successfully sent electronic cheque to {} for transaction ID: {}",
        chequeDetails.getRecipientEmail(),
        chequeDetails.getTransactionId()
      );
    } catch (Exception e) { // Ловим ResourceException и другие
      log.error(
        "JCA-Style EIS: Failed to send electronic cheque for transaction ID {}: {}",
        chequeDetails.getTransactionId(),
        e.getMessage(),
        e
      );
      throw e; // Перебрасываем, чтобы вызывающий код знал об ошибке
    } finally {
      if (emailConnection != null) {
        try {
          emailConnection.close();
        } catch (Exception e) {
          log.warn(
            "JCA-Style EIS: Error closing EmailConnection: {}",
            e.getMessage()
          );
        }
      }
    }
  }
}
