package ru.sberbank.sbp.lab2.transfer_service.config.properties;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.jta.atomikos.datasource")
@Validated
public class AtomikosDataSourceProperties {

  @NotEmpty
  private String uniqueResourceName;

  @NotNull
  private XaProperties xaProperties = new XaProperties();

  private int maxPoolSize = 10;
  private int minPoolSize = 2;
  private String testQuery = "SELECT 1";

  // --- Геттеры и Сеттеры ---

  public String getUniqueResourceName() {
    return uniqueResourceName;
  }

  public void setUniqueResourceName(String uniqueResourceName) {
    this.uniqueResourceName = uniqueResourceName;
  }

  public XaProperties getXaProperties() {
    return xaProperties;
  }

  public void setXaProperties(XaProperties xaProperties) {
    this.xaProperties = xaProperties;
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public void setMaxPoolSize(int maxPoolSize) {
    this.maxPoolSize = maxPoolSize;
  }

  public int getMinPoolSize() {
    return minPoolSize;
  }

  public void setMinPoolSize(int minPoolSize) {
    this.minPoolSize = minPoolSize;
  }

  public String getTestQuery() {
    return testQuery;
  }

  public void setTestQuery(String testQuery) {
    this.testQuery = testQuery;
  }

  public static class XaProperties {

    @NotEmpty
    private String url;

    @NotEmpty
    private String user;

    private String password;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }
}
