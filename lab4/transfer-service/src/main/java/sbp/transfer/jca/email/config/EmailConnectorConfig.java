package sbp.transfer.jca.email.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sbp.transfer.jca.email.spi.EmailManagedConnectionFactoryImpl;

@Configuration
public class EmailConnectorConfig {

  @Value("${EIS_MAIL_HOST}")
  private String eisMailHost;

  @Value("${EIS_MAIL_PORT}")
  private int eisMailPort;

  @Value("${EIS_MAIL_USER}")
  private String eisMailUser;

  @Value("${EIS_MAIL_PASSWORD}")
  private String eisMailPassword;

  @Value("${EIS_MAIL_FROM}")
  private String eisMailFrom;

  @Bean
  public EmailManagedConnectionFactoryImpl emailManagedConnectionFactory() {
    EmailManagedConnectionFactoryImpl mcf =
      new EmailManagedConnectionFactoryImpl();
    mcf.setHost(eisMailHost);
    mcf.setPort(eisMailPort);
    mcf.setUsername(eisMailUser);
    mcf.setPassword(eisMailPassword);
    mcf.setMailFrom(eisMailFrom);
    return mcf;
  }

  @Bean(name = "emailConnectionFactory")
  public jakarta.resource.cci.ConnectionFactory cciEmailConnectionFactory(
    EmailManagedConnectionFactoryImpl emailManagedConnectionFactory
  ) throws jakarta.resource.ResourceException {
    return (jakarta.resource.cci.ConnectionFactory) emailManagedConnectionFactory.createConnectionFactory();
  }
}
