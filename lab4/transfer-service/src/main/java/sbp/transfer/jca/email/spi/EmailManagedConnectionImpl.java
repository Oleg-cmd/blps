package sbp.transfer.jca.email.spi;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import sbp.transfer.jca.email.internal.EmailConnectionImpl;

public class EmailManagedConnectionImpl implements ManagedConnection {

  private final EmailManagedConnectionFactoryImpl mcf;
  private JavaMailSender mailSender;
  private PrintWriter logWriter;
  private final List<ConnectionEventListener> listeners = new ArrayList<>();
  private EmailConnectionImpl currentAppLevelConnectionHandle;

  public EmailManagedConnectionImpl(EmailManagedConnectionFactoryImpl mcf) {
    this.mcf = mcf;
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(mcf.getHost());
    sender.setPort(mcf.getPort());
    if (mcf.getUsername() != null && !mcf.getUsername().isEmpty()) {
      sender.setUsername(mcf.getUsername());
      sender.setPassword(mcf.getPassword());
      Properties props = sender.getJavaMailProperties();
      props.put("mail.smtp.auth", "true");
      props.put("mail.smtp.starttls.enable", "true");
    }
    this.mailSender = sender;
  }

  @Override
  public Object getConnection(
    Subject subject,
    ConnectionRequestInfo cxRequestInfo
  ) throws ResourceException {
    if (currentAppLevelConnectionHandle != null) {
      throw new ResourceException(
        "Connection handle already exists for this managed connection"
      );
    }
    currentAppLevelConnectionHandle = new EmailConnectionImpl(
      this.mailSender,
      mcf.getMailFrom()
    );
    return currentAppLevelConnectionHandle;
  }

  @Override
  public void destroy() throws ResourceException {
    this.mailSender = null;
  }

  @Override
  public void cleanup() throws ResourceException {
    if (currentAppLevelConnectionHandle != null) {
      currentAppLevelConnectionHandle = null;
    }
  }

  @Override
  public void associateConnection(Object connection) throws ResourceException {
    if (connection instanceof EmailConnectionImpl) {
      this.currentAppLevelConnectionHandle = (EmailConnectionImpl) connection;
    } else if (connection == null) {
      this.currentAppLevelConnectionHandle = null;
    } else {
      throw new ResourceException("Invalid connection handle type");
    }
  }

  @Override
  public void addConnectionEventListener(ConnectionEventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeConnectionEventListener(ConnectionEventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public XAResource getXAResource() throws ResourceException {
    return null;
  }

  @Override
  public LocalTransaction getLocalTransaction() throws ResourceException {
    throw new jakarta.resource.NotSupportedException(
      "Local transactions not supported"
    );
  }

  @Override
  public ManagedConnectionMetaData getMetaData() throws ResourceException {
    return new ManagedConnectionMetaData() {
      @Override
      public String getEISProductName() throws ResourceException {
        return "SMTP Email Service";
      }

      @Override
      public String getEISProductVersion() throws ResourceException {
        return "N/A";
      }

      @Override
      public int getMaxConnections() throws ResourceException {
        return 0;
      }

      @Override
      public String getUserName() throws ResourceException {
        return mcf.getUsername();
      }
    };
  }

  @Override
  public void setLogWriter(PrintWriter out) throws ResourceException {
    this.logWriter = out;
  }

  @Override
  public PrintWriter getLogWriter() throws ResourceException {
    return this.logWriter;
  }
}
