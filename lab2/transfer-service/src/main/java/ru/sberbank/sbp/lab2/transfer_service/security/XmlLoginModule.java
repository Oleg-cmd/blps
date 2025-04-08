package ru.sberbank.sbp.lab2.transfer_service.security.jaas;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlLoginModule implements LoginModule {

  private static final Logger log = LoggerFactory.getLogger(
    XmlLoginModule.class
  );

  // Состояние модуля
  private Subject subject;
  private CallbackHandler callbackHandler;
  private Map<String, ?> options;

  // Данные пользователя после успешной аутентификации
  private String authenticatedUsername;
  private final List<Principal> principalsToAdd = new ArrayList<>(); // Сохраняем здесь до commit
  private boolean succeeded = false;

  // Используем BCrypt для проверки паролей
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Override
  public void initialize(
    Subject subject,
    CallbackHandler callbackHandler,
    Map<String, ?> sharedState,
    Map<String, ?> options
  ) {
    this.subject = subject;
    this.callbackHandler = callbackHandler;
    this.options = options;
    log.trace("XmlLoginModule initialized."); // Используем TRACE для детальных логов
  }

  @Override
  public boolean login() throws LoginException {
    log.trace("XmlLoginModule login starting...");
    succeeded = false; // Сбрасываем флаг при каждой попытке входа
    principalsToAdd.clear(); // Очищаем временные principals

    if (callbackHandler == null) {
      throw new LoginException("No CallbackHandler provided");
    }

    NameCallback nameCallback = new NameCallback("Username: ");
    PasswordCallback passwordCallback = new PasswordCallback(
      "Password: ",
      false
    );
    try {
      callbackHandler.handle(new Callback[] { nameCallback, passwordCallback });
    } catch (IOException | UnsupportedCallbackException e) {
      log.error("Error handling callbacks", e);
      throw new LoginException("Callback handling failed: " + e.getMessage());
    }

    String username = nameCallback.getName();
    char[] passwordChars = passwordCallback.getPassword();
    // Очищаем пароль как можно раньше
    if (passwordCallback != null) {
      passwordCallback.clearPassword();
    }
    if (username == null || passwordChars == null) {
      log.warn("Username or password not provided via callbacks.");
      return false; // Аутентификация не удалась
    }
    String password = new String(passwordChars);

    log.debug("Attempting login for user: {}", username);

    String xmlFilePath = (String) options.get("xmlUserFile");
    if (xmlFilePath == null || xmlFilePath.isEmpty()) {
      throw new LoginException(
        "XML user file path not specified in JAAS config options (xmlUserFile)"
      );
    }
    log.debug("Loading users from: {}", xmlFilePath);

    try (
      InputStream xmlStream = new ClassPathResource(
        xmlFilePath.replace("classpath:", "")
      ).getInputStream()
    ) {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlStream);
      doc.getDocumentElement().normalize();

      NodeList nList = doc.getElementsByTagName("user");

      for (int i = 0; i < nList.getLength(); i++) {
        Element userElement = (Element) nList.item(i);
        String xmlUsername = userElement.getAttribute("username");

        if (username.equals(xmlUsername)) {
          String xmlPasswordHash = userElement.getAttribute("password");
          String xmlRoles = userElement.getAttribute("roles");

          if (passwordEncoder.matches(password, xmlPasswordHash)) {
            log.info("User '{}' authenticated successfully.", username);
            succeeded = true;
            authenticatedUsername = username;

            // Добавляем Principal для имени пользователя
            principalsToAdd.add(new UserPrincipal(authenticatedUsername));

            // Добавляем роли как RolePrincipal
            if (xmlRoles != null && !xmlRoles.isEmpty()) {
              Arrays.stream(xmlRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .forEach(role -> principalsToAdd.add(new RolePrincipal(role))); // Сохраняем во временный список
            }
            log.debug(
              "User '{}' granted principals (to be added on commit): {}",
              username,
              principalsToAdd
            );
            return true; // Аутентификация успешна
          } else {
            log.warn("Password mismatch for user '{}'", username);
            return false; // Неверный пароль
          }
        }
      }

      log.warn("User '{}' not found in {}", username, xmlFilePath);
      return false; // Пользователь не найден
    } catch (Exception e) {
      log.error("Error during XML parsing or authentication", e);
      throw new LoginException(
        "Authentication failed due to internal error: " + e.getMessage()
      );
    }
  }

  @Override
  public boolean commit() throws LoginException {
    if (!succeeded) {
      log.trace(
        "XmlLoginModule commit: authentication failed or login() not called successfully."
      );
      clearState(); // Очищаем на всякий случай
      return false;
    }

    // Добавляем сохраненные principals в Subject
    if (subject.isReadOnly()) {
      throw new LoginException("Subject is Readonly, failed to add Principals");
    }
    for (Principal p : principalsToAdd) {
      if (!subject.getPrincipals().contains(p)) {
        subject.getPrincipals().add(p);
        log.trace("Added Principal '{}' to subject", p);
      } else {
        log.trace("Principal '{}' already exists in subject", p);
      }
    }

    log.debug(
      "XmlLoginModule commit successful for user '{}'",
      authenticatedUsername
    );
    return true;
  }

  @Override
  public boolean abort() throws LoginException {
    log.trace("XmlLoginModule abort called.");
    if (!succeeded) {
      log.trace("Abort: Login never succeeded.");
      return false; // Ничего не делаем, если login не удался
    } else {
      // Если login удался, но commit не был вызван или не удался,
      // очищаем состояние
      log.trace(
        "Abort: Login succeeded but commit possibly failed. Clearing state."
      );
      clearState();
      succeeded = false;
      return true;
    }
  }

  @Override
  public boolean logout() throws LoginException {
    log.debug(
      "XmlLoginModule logout called for subject containing principals: {}",
      subject.getPrincipals()
    );
    if (subject.isReadOnly()) {
      throw new LoginException(
        "Subject is Readonly, failed to remove Principals"
      );
    }
    // Удаляем principals, добавленные этим модулем (из principalsToAdd, если они были успешно добавлены)
    // principalsToAdd уже очищен в abort/commit/login, так что используем то, что знаем
    Set<Principal> principalsToRemove = new HashSet<>();
    if (authenticatedUsername != null) {
      principalsToRemove.add(new UserPrincipal(authenticatedUsername));
    }
    // Ищем добавленные роли по имени, т.к. список principalsToAdd может быть уже очищен
    // Это не идеально, но лучше, чем ничего. В реальном мире хранили бы добавленные Principals.
    subject.getPrincipals(RolePrincipal.class).forEach(principalsToRemove::add);

    if (!principalsToRemove.isEmpty()) {
      int initialSize = subject.getPrincipals().size();
      subject.getPrincipals().removeAll(principalsToRemove);
      log.debug(
        "Attempted to remove principals: {}. Subject principals before: {}, after: {}",
        principalsToRemove,
        initialSize,
        subject.getPrincipals().size()
      );
    } else {
      log.debug("No principals identified for removal during logout.");
    }

    clearState();
    succeeded = false;
    log.debug("Logout completed.");
    return true;
  }

  private void clearState() {
    authenticatedUsername = null;
    principalsToAdd.clear();
    // succeeded сбрасывается в login() и abort()
  }

  // Простой внутренний класс для UserPrincipal
  private static class UserPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;

    UserPrincipal(String name) {
      Objects.requireNonNull(name, "UserPrincipal name cannot be null");
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      return (
        this == o ||
        (o instanceof UserPrincipal &&
          Objects.equals(name, ((UserPrincipal) o).name))
      );
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }

    @Override
    public String toString() {
      return "UserPrincipal{" + "name='" + name + '\'' + '}';
    }
  }
}
