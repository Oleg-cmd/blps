package sbp.transfer.jca.email;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.cci.ConnectionSpec;
import jakarta.resource.cci.RecordFactory;
import jakarta.resource.cci.ResourceAdapterMetaData;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ManagedConnectionFactory;
import javax.naming.NamingException;
import javax.naming.Reference;

public class EmailConnectionFactoryImpl implements ConnectionFactory {

  private static final long serialVersionUID = 1L;

  private final ManagedConnectionFactory mcf;
  private final ConnectionManager cm;

  public EmailConnectionFactoryImpl(
    ManagedConnectionFactory mcf,
    ConnectionManager cm
  ) {
    this.mcf = mcf;
    this.cm = cm;
  }

  @Override
  public Connection getConnection() throws ResourceException {
    if (cm != null) {
      return (Connection) cm.allocateConnection(this.mcf, null);
    } else {
      sbp.transfer.jca.email.spi.EmailManagedConnectionImpl newMc =
        (sbp.transfer.jca.email.spi.EmailManagedConnectionImpl) this.mcf.createManagedConnection(
            null,
            null
          );
      return (Connection) newMc.getConnection(null, null);
    }
  }

  @Override
  public Connection getConnection(ConnectionSpec properties)
    throws ResourceException {
    if (cm != null) {
      return (Connection) cm.allocateConnection(this.mcf, null);
    } else {
      sbp.transfer.jca.email.spi.EmailManagedConnectionImpl newMc =
        (sbp.transfer.jca.email.spi.EmailManagedConnectionImpl) this.mcf.createManagedConnection(
            null,
            null
          );
      return (Connection) newMc.getConnection(null, null);
    }
  }

  @Override
  public RecordFactory getRecordFactory() throws ResourceException {
    throw new jakarta.resource.NotSupportedException(
      "RecordFactory not supported"
    );
  }

  @Override
  public ResourceAdapterMetaData getMetaData() throws ResourceException {
    return new ResourceAdapterMetaData() {
      @Override
      public String getAdapterVersion() {
        return "1.0";
      }

      @Override
      public String getAdapterVendorName() {
        return "SBP Lab";
      }

      @Override
      public String getAdapterName() {
        return "Email JCA-Style Adapter";
      }

      @Override
      public String getAdapterShortDescription() {
        return "Sends emails via SMTP";
      }

      @Override
      public String getSpecVersion() {
        return "1.7";
        /* Версия JCA, которой пытаемся соответствовать */
      }

      @Override
      public String[] getInteractionSpecsSupported() {
        return new String[0];
        /* Можно указать InteractionSpec */
      }

      @Override
      public boolean supportsExecuteWithInputAndOutputRecord() {
        return false;
      }

      @Override
      public boolean supportsExecuteWithInputRecordOnly() {
        return false;
      }

      @Override
      public boolean supportsLocalTransactionDemarcation() {
        return false;
      }
    };
  }

  private Reference reference;

  @Override
  public void setReference(Reference reference) {
    this.reference = reference;
  }

  @Override
  public Reference getReference() throws NamingException {
    return this.reference;
  }
}
