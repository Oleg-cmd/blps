package sbp.dto;

import sbp.dto.eis.ChequeDetailsDTO;

/**
 * Interface for interacting with the External Information System (EIS).
 */
public interface EisService { // Изменили class на interface
  /**
   * Sends an electronic cheque.
   *
   * @param chequeDetails The details of the cheque to be sent.
   * @throws Exception if sending the cheque fails.
   */
  void sendElectronicCheque(ChequeDetailsDTO chequeDetails) throws Exception;
}
