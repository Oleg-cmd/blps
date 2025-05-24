package ru.sberbank.sbp.lab2.transfer_service.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import javax.sql.DataSource;
import org.postgresql.xa.PGXADataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.sberbank.sbp.lab2.transfer_service.config.properties.AtomikosDataSourceProperties;

@Configuration
@EnableConfigurationProperties(AtomikosDataSourceProperties.class)
public class DataSourceConfig {

  @Bean(name = "dataSource", initMethod = "init", destroyMethod = "close")
  public DataSource dataSource(AtomikosDataSourceProperties properties) {
    PGXADataSource pgXaDataSource = new PGXADataSource();

    pgXaDataSource.setUrl(properties.getXaProperties().getUrl());
    pgXaDataSource.setUser(properties.getXaProperties().getUser());
    pgXaDataSource.setPassword(properties.getXaProperties().getPassword());

    AtomikosDataSourceBean atomikosDataSourceBean =
      new AtomikosDataSourceBean();
    atomikosDataSourceBean.setXaDataSource(pgXaDataSource);
    atomikosDataSourceBean.setUniqueResourceName(
      properties.getUniqueResourceName()
    );
    atomikosDataSourceBean.setMaxPoolSize(properties.getMaxPoolSize());
    atomikosDataSourceBean.setMinPoolSize(properties.getMinPoolSize());
    atomikosDataSourceBean.setTestQuery(properties.getTestQuery());

    System.out.println(
      "Initializing AtomikosDataSourceBean for transfer-service with URL: " +
      properties.getXaProperties().getUrl()
    );

    return atomikosDataSourceBean;
  }
}
