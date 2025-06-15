package sbp.transfer.jca.email;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import sbp.dto.eis.ChequeDetailsDTO;

public interface EmailConnection extends Connection {
  void sendElectronicCheque(ChequeDetailsDTO chequeDetails)
    throws ResourceException;
}
