package sbp.account.config; // Убедитесь, что пакет правильный

import com.atomikos.jdbc.AtomikosDataSourceBean;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TestAtomikosConfiguration {

  @Bean(initMethod = "init", destroyMethod = "close")
  @Primary
  public DataSource dataSource() {
    JdbcDataSource h2XaDataSource = new JdbcDataSource();
    h2XaDataSource.setURL(
      "jdbc:h2:mem:account_test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,VALUE"
    );
    h2XaDataSource.setUser("sa");
    h2XaDataSource.setPassword("");

    AtomikosDataSourceBean atomikosDataSourceBean =
      new AtomikosDataSourceBean();
    atomikosDataSourceBean.setXaDataSource(h2XaDataSource);
    atomikosDataSourceBean.setUniqueResourceName("accountH2XaTest");
    atomikosDataSourceBean.setMaxPoolSize(10);
    atomikosDataSourceBean.setMinPoolSize(1);
    atomikosDataSourceBean.setTestQuery("SELECT 1");
    return atomikosDataSourceBean;
  }
}
