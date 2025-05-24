package sbp.account.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import javax.sql.DataSource;
import org.postgresql.xa.PGXADataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class DataSourceConfig {

  private static final Logger log = LoggerFactory.getLogger(
    DataSourceConfig.class
  );

  @Value("${spring.jta.atomikos.datasource.unique-resource-name}")
  private String uniqueResourceName;

  // --- Используем свойства для PGXADataSource ---
  @Value("${spring.jta.atomikos.datasource.xa-properties.serverName}")
  private String serverName;

  @Value("${spring.jta.atomikos.datasource.xa-properties.portNumber}")
  private int portNumber;

  @Value("${spring.jta.atomikos.datasource.xa-properties.databaseName}")
  private String databaseName;

  @Value("${spring.jta.atomikos.datasource.xa-properties.user}")
  private String user;

  @Value("${spring.jta.atomikos.datasource.xa-properties.password}")
  private String password;

  // --- Свойства для AtomikosDataSourceBean ---
  @Value("${spring.jta.atomikos.datasource.max-pool-size:10}")
  private int maxPoolSize;

  @Value("${spring.jta.atomikos.datasource.min-pool-size:2}")
  private int minPoolSize;

  @Value("${spring.jta.atomikos.datasource.test-query:SELECT 1}")
  private String testQuery;

  @Value("${spring.jta.atomikos.datasource.borrow-connection-timeout:60}")
  private int borrowConnectionTimeout;

  @Bean(name = "dataSource", initMethod = "init", destroyMethod = "close")
  @Primary
  public DataSource dataSource() {
    log.info(
      "--- account-service Custom Atomikos DataSourceConfig :: Creating 'dataSource' bean ---"
    );
    log.info("DB Server Name: {}", serverName);
    log.info("DB Port Number: {}", portNumber);
    log.info("DB Database Name: {}", databaseName);
    log.info("DB User: {}", user);
    log.info(
      "DB Password is set: {}",
      (password != null && !password.isEmpty())
    );
    log.info("Atomikos Unique Resource Name: {}", uniqueResourceName);

    if (
      serverName == null ||
      serverName.trim().isEmpty() ||
      databaseName == null ||
      databaseName.trim().isEmpty()
    ) {
      String errorMsg =
        "JDBC serverName or databaseName is missing for account-service DataSource configuration.";
      log.error(errorMsg);
      throw new IllegalStateException(errorMsg);
    }

    PGXADataSource pgXaDataSource = new PGXADataSource();
    pgXaDataSource.setServerName(serverName);
    pgXaDataSource.setPortNumber(portNumber);
    pgXaDataSource.setDatabaseName(databaseName);
    pgXaDataSource.setUser(user);
    pgXaDataSource.setPassword(password);

    AtomikosDataSourceBean atomikosDataSourceBean =
      new AtomikosDataSourceBean();
    atomikosDataSourceBean.setXaDataSource(pgXaDataSource);
    atomikosDataSourceBean.setUniqueResourceName(uniqueResourceName);
    atomikosDataSourceBean.setMaxPoolSize(maxPoolSize);
    atomikosDataSourceBean.setMinPoolSize(minPoolSize);
    atomikosDataSourceBean.setTestQuery(testQuery);
    atomikosDataSourceBean.setBorrowConnectionTimeout(borrowConnectionTimeout);

    log.info(
      "Custom AtomikosDataSourceBean for account-service created successfully."
    );
    return atomikosDataSourceBean;
  }
}
