package ru.sberbank.sbp.lab2.transfer_service.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import java.util.Properties;
import javax.sql.DataSource;
import org.postgresql.xa.PGXADataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

  @Value("${spring.jta.atomikos.datasource.unique-resource-name}")
  private String uniqueResourceName;

  @Value("${spring.jta.atomikos.datasource.xa-properties.url}")
  private String url;

  @Value("${spring.jta.atomikos.datasource.xa-properties.user}")
  private String user;

  @Value("${spring.jta.atomikos.datasource.xa-properties.password}")
  private String password;

  @Value("${spring.jta.atomikos.datasource.max-pool-size:10}")
  private int maxPoolSize;

  @Value("${spring.jta.atomikos.datasource.min-pool-size:2}")
  private int minPoolSize;

  @Value("${spring.jta.atomikos.datasource.test-query:SELECT 1}")
  private String testQuery;

  @Bean(name = "dataSource", initMethod = "init", destroyMethod = "close")
  // @DependsOn не используем
  public DataSource dataSource() {
    PGXADataSource pgXaDataSource = new PGXADataSource();
    pgXaDataSource.setUrl(url);
    pgXaDataSource.setUser(user);
    pgXaDataSource.setPassword(password);

    AtomikosDataSourceBean atomikosDataSourceBean =
      new AtomikosDataSourceBean();
    atomikosDataSourceBean.setXaDataSource(pgXaDataSource);
    atomikosDataSourceBean.setUniqueResourceName(uniqueResourceName);
    atomikosDataSourceBean.setMaxPoolSize(maxPoolSize);
    atomikosDataSourceBean.setMinPoolSize(minPoolSize);
    atomikosDataSourceBean.setTestQuery(testQuery);

    System.out.println(
      "Initializing AtomikosDataSourceBean for transfer-service with URL: " +
      url
    );

    return atomikosDataSourceBean;
  }
}
