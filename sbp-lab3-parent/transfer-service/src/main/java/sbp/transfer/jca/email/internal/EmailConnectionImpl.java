package sbp.transfer.jca.email.internal;

import jakarta.mail.internet.MimeMessage;
import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.ConnectionMetaData;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.LocalTransaction;
import jakarta.resource.cci.ResultSetInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import sbp.dto.eis.ChequeDetailsDTO;
import sbp.transfer.jca.email.EmailConnection;

@Slf4j
public class EmailConnectionImpl implements EmailConnection {

  private final JavaMailSender mailSender;
  private final String mailFrom;

  public EmailConnectionImpl(JavaMailSender mailSender, String mailFrom) {
    this.mailSender = mailSender;
    this.mailFrom = mailFrom;
  }

  @Override
  public void sendElectronicCheque(ChequeDetailsDTO chequeDetails)
    throws ResourceException {
    log.debug(
      "EmailConnection: Sending electronic cheque for transaction ID: {}",
      chequeDetails.getTransactionId()
    );
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(
        mimeMessage,
        true,
        "UTF-8"
      );
      helper.setFrom(mailFrom);
      helper.setTo(chequeDetails.getRecipientEmail());
      helper.setSubject(chequeDetails.getSubject());
      String emailBody = buildChequeEmailBody(chequeDetails);
      helper.setText(emailBody, true);
      mailSender.send(mimeMessage);
      log.debug(
        "EmailConnection: Successfully sent electronic cheque to {} for transaction ID: {}",
        chequeDetails.getRecipientEmail(),
        chequeDetails.getTransactionId()
      );
    } catch (Exception e) {
      log.error(
        "EmailConnection: Error sending email for transaction ID {}: {}",
        chequeDetails.getTransactionId(),
        e.getMessage(),
        e
      );
      throw new ResourceException("Failed to send email", e);
    }
  }

  @Override
  public void close() throws ResourceException {
    log.debug(
      "EmailConnection: close() called. No specific resources to release for JavaMailSender."
    );
  }

  private String buildChequeEmailBody(ChequeDetailsDTO details) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html><body>");
    sb.append("<h1>Electronic Cheque</h1>");
    sb
      .append("<p><strong>Transaction ID:</strong> ")
      .append(details.getTransactionId())
      .append("</p>");
    sb
      .append("<p><strong>Date:</strong> ")
      .append(details.getTransactionTimestamp())
      .append("</p>");
    sb
      .append("<p><strong>Sender:</strong> ")
      .append(details.getSenderInfo())
      .append("</p>");
    sb
      .append("<p><strong>Recipient:</strong> ")
      .append(details.getRecipientInfo())
      .append("</p>");
    sb
      .append("<p><strong>Amount:</strong> ")
      .append(details.getAmount())
      .append("</p>");
    sb
      .append("<p><strong>Details:</strong> ")
      .append(details.getOperationDetails())
      .append("</p>");
    sb.append("<hr><p>Thank you for using SBP!</p>");
    sb.append("</body></html>");
    return sb.toString();
  }

  // --- Реализация методов из jakarta.resource.cci.Connection ---

  @Override
  public Interaction createInteraction() throws ResourceException {
    throw new NotSupportedException(
      "Interactions not supported by this EmailConnection"
    );
  }

  @Override
  public LocalTransaction getLocalTransaction() throws ResourceException {
    throw new NotSupportedException(
      "Local transactions not supported at CCI level for this EmailConnection"
    );
  }

  @Override
  public ConnectionMetaData getMetaData() throws ResourceException {
    return new ConnectionMetaData() {
      @Override
      public String getEISProductName() throws ResourceException {
        return "SMTP Email Service";
      }

      @Override
      public String getEISProductVersion() throws ResourceException {
        return "N/A";
      }

      @Override
      public String getUserName() throws ResourceException {
        return null;
      }
    };
  }

  @Override
  public ResultSetInfo getResultSetInfo() throws ResourceException {
    throw new NotSupportedException(
      "ResultSetInfo not supported by this EmailConnection"
    );
  }
}
