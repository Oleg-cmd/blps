package ru.sberbank.sbp.lab2.account_service.config;

import com.atomikos.jdbc.AtomikosDataSourceBean; // Важно: это класс Atomikos
import java.util.Properties;
import javax.sql.DataSource;
import org.postgresql.xa.PGXADataSource; // Используем конкретный XADataSource
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

  // Внедряем значения из application.properties (секция spring.jta.atomikos.datasource)
  @Value("${spring.jta.atomikos.datasource.unique-resource-name}")
  private String uniqueResourceName;

  @Value("${spring.jta.atomikos.datasource.xa-properties.url}")
  private String url;

  @Value("${spring.jta.atomikos.datasource.xa-properties.user}")
  private String user;

  @Value("${spring.jta.atomikos.datasource.xa-properties.password}")
  private String password;

  @Value("${spring.jta.atomikos.datasource.max-pool-size:10}") // Значение по умолчанию
  private int maxPoolSize;

  @Value("${spring.jta.atomikos.datasource.min-pool-size:2}") // Значение по умолчанию
  private int minPoolSize;

  @Value("${spring.jta.atomikos.datasource.test-query:SELECT 1}") // Значение по умолчанию
  private String testQuery;

  @Bean(name = "dataSource", initMethod = "init", destroyMethod = "close")
  public DataSource dataSource() {
    // 1. Создаем реальный XA DataSource
    PGXADataSource pgXaDataSource = new PGXADataSource();
    pgXaDataSource.setUrl(url);
    pgXaDataSource.setUser(user);
    pgXaDataSource.setPassword(password);

    // 2. Оборачиваем его в AtomikosDataSourceBean
    AtomikosDataSourceBean atomikosDataSourceBean =
      new AtomikosDataSourceBean();
    atomikosDataSourceBean.setXaDataSource(pgXaDataSource);
    atomikosDataSourceBean.setUniqueResourceName(uniqueResourceName);
    atomikosDataSourceBean.setMaxPoolSize(maxPoolSize);
    atomikosDataSourceBean.setMinPoolSize(minPoolSize);
    atomikosDataSourceBean.setTestQuery(testQuery);
    // atomikosDataSourceBean.setBorrowConnectionTimeout(60); // Таймаут ожидания соединения из пула (сек)
    // atomikosDataSourceBean.setReapTimeout(0); // Таймаут для "мертвых" соединений (0 - отключено)
    // atomikosDataSourceBean.setMaxIdleTime(60); // Время простоя перед удалением из пула

    System.out.println("Initializing AtomikosDataSourceBean with URL: " + url); // Отладочный вывод

    return atomikosDataSourceBean;
  }
}
