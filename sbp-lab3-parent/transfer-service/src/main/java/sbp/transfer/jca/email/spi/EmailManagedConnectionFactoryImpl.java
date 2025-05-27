package sbp.transfer.jca.email.spi;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import java.io.PrintWriter;
import java.util.Set;
import javax.security.auth.Subject;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EmailManagedConnectionFactoryImpl
  implements ManagedConnectionFactory {

  private static final long serialVersionUID = 1L;

  private String host;
  private int port;
  private String username;
  private String password;
  private String mailFrom;
  private transient PrintWriter logWriter;

  public EmailManagedConnectionFactoryImpl() {}

  @Override
  public Object createConnectionFactory(ConnectionManager cxManager)
    throws ResourceException {
    return new sbp.transfer.jca.email.EmailConnectionFactoryImpl(
      this,
      cxManager
    );
  }

  @Override
  public Object createConnectionFactory() throws ResourceException {
    return new sbp.transfer.jca.email.EmailConnectionFactoryImpl(this, null);
  }

  @Override
  public ManagedConnection createManagedConnection(
    Subject subject,
    ConnectionRequestInfo cxRequestInfo
  ) throws ResourceException {
    return new EmailManagedConnectionImpl(this);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public ManagedConnection matchManagedConnections(
    Set connectionSet,
    Subject subject,
    ConnectionRequestInfo cxRequestInfo
  ) throws ResourceException {
    if (connectionSet == null || connectionSet.isEmpty()) {
      return null;
    }
    return (ManagedConnection) connectionSet.stream().findFirst().orElse(null);
  }

  @Override
  public void setLogWriter(PrintWriter out) throws ResourceException {
    this.logWriter = out;
  }

  @Override
  public PrintWriter getLogWriter() throws ResourceException {
    return this.logWriter;
  }

  // equals() и hashCode() важны для ManagedConnectionFactory
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    EmailManagedConnectionFactoryImpl other =
      (EmailManagedConnectionFactoryImpl) obj;
    return (
      port == other.port &&
      java.util.Objects.equals(host, other.host) &&
      java.util.Objects.equals(username, other.username) &&
      java.util.Objects.equals(password, other.password) &&
      java.util.Objects.equals(mailFrom, other.mailFrom)
    );
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(host, port, username, password, mailFrom);
  }
}
