package sbp.transfer.jca.email;

import jakarta.resource.ResourceException;
import sbp.dto.eis.ChequeDetailsDTO;

public interface EmailConnection {
  void sendElectronicCheque(ChequeDetailsDTO chequeDetails)
    throws ResourceException;
  void close() throws ResourceException;
}
