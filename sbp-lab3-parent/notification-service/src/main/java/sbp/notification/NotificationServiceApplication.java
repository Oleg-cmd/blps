package sbp.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication(
  exclude = {
    DataSourceAutoConfiguration.class, // Отключаем автоконфигурацию DataSource
    HibernateJpaAutoConfiguration.class, // Отключаем автоконфигурацию JPA/Hibernate
  }
)
@EnableJms
public class NotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
