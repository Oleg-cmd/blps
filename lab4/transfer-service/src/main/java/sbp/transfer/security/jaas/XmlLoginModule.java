package sbp.transfer.security.jaas;

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

  private Subject subject;
  private CallbackHandler callbackHandler;
  private Map<String, ?> options;

  private String authenticatedUsername;
  private final List<Principal> principalsToAdd = new ArrayList<>();
  private boolean loginSucceeded = false;

  // Используем BCrypt из Spring Security
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  private static class UserPrincipalImpl implements Principal, Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;

    UserPrincipalImpl(String name) {
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
        (o instanceof UserPrincipalImpl &&
          Objects.equals(name, ((UserPrincipalImpl) o).name))
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
    log.trace("XmlLoginModule initialized.");
  }

  @Override
  public boolean login() throws LoginException {
    log.trace("XmlLoginModule login starting...");
    loginSucceeded = false;
    principalsToAdd.clear();

    if (callbackHandler == null) {
      throw new LoginException("Error: CallbackHandler is null");
    }

    NameCallback nameCallback = new NameCallback("Username: ");
    PasswordCallback passwordCallback = new PasswordCallback(
      "Password: ",
      false
    );

    try {
      callbackHandler.handle(new Callback[] { nameCallback, passwordCallback });
    } catch (IOException | UnsupportedCallbackException e) {
      log.error("Callback handling failed", e);
      throw new LoginException("Callback handling failed: " + e.getMessage());
    }

    String username = nameCallback.getName();
    char[] passwordChars = passwordCallback.getPassword();
    if (passwordCallback != null) {
      passwordCallback.clearPassword(); // Очищаем пароль из колбэка
    }

    if (username == null || passwordChars == null) {
      log.warn("Username or password not provided.");
      return false;
    }
    String rawPassword = new String(passwordChars);
    // Очищаем массив char[] с паролем
    Arrays.fill(passwordChars, ' ');

    log.debug("Attempting login for user: {}", username);

    String xmlFilePathOption = (String) options.get("xmlUserFile");
    if (xmlFilePathOption == null || xmlFilePathOption.isEmpty()) {
      throw new LoginException(
        "JAAS config option 'xmlUserFile' not specified."
      );
    }
    log.debug("Loading users from XML: {}", xmlFilePathOption);

    // Удаляем "classpath:" префикс, если он есть, так как ClassPathResource ожидает путь без него
    String actualXmlPath = xmlFilePathOption.startsWith("classpath:")
      ? xmlFilePathOption.substring("classpath:".length())
      : xmlFilePathOption;

    try (
      InputStream xmlStream = new ClassPathResource(
        actualXmlPath
      ).getInputStream()
    ) {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      // Защита от XXE (хорошо бы иметь)
      dbFactory.setFeature(
        "http://apache.org/xml/features/disallow-doctype-decl",
        true
      );
      dbFactory.setFeature(
        "http://xml.org/sax/features/external-general-entities",
        false
      );
      dbFactory.setFeature(
        "http://xml.org/sax/features/external-parameter-entities",
        false
      );
      dbFactory.setXIncludeAware(false);
      dbFactory.setExpandEntityReferences(false);

      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlStream);
      doc.getDocumentElement().normalize();

      NodeList userNodes = doc.getElementsByTagName("user");
      for (int i = 0; i < userNodes.getLength(); i++) {
        Element userElement = (Element) userNodes.item(i);
        String xmlUsername = userElement.getAttribute("username");

        if (username.equals(xmlUsername)) {
          String xmlPasswordHash = userElement.getAttribute("password");
          String xmlRoles = userElement.getAttribute("roles");

          if (passwordEncoder.matches(rawPassword, xmlPasswordHash)) {
            log.info("User '{}' authenticated successfully.", username);
            loginSucceeded = true;
            authenticatedUsername = username;

            principalsToAdd.add(new UserPrincipalImpl(authenticatedUsername));
            if (xmlRoles != null && !xmlRoles.isEmpty()) {
              Arrays.stream(xmlRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .forEach(role -> principalsToAdd.add(new RolePrincipal(role)));
            }
            log.debug(
              "User '{}' granted principals (to be added on commit): {}",
              username,
              principalsToAdd
            );
            return true;
          } else {
            log.warn("Password mismatch for user '{}'", username);
            return false; // Пользователь найден, но пароль неверный, дальнейший поиск не нужен
          }
        }
      }
      log.warn("User '{}' not found in XML.", username);
      return false; // Пользователь не найден
    } catch (Exception e) {
      log.error(
        "Error during XML parsing or authentication for user '{}'",
        username,
        e
      );
      throw new LoginException(
        "Authentication failed due to internal error: " + e.getMessage()
      );
    }
  }

  @Override
  public boolean commit() throws LoginException {
    if (!loginSucceeded) {
      log.trace("Commit: Login not successful.");
      clearState();
      return false;
    }
    if (subject.isReadOnly()) {
      throw new LoginException("Subject is Readonly, cannot add Principals.");
    }
    subject.getPrincipals().addAll(principalsToAdd);
    log.debug(
      "Commit successful for user '{}'. Principals added: {}",
      authenticatedUsername,
      principalsToAdd
    );
    return true;
  }

  @Override
  public boolean abort() throws LoginException {
    log.trace("Abort called.");
    if (!loginSucceeded) {
      return false;
    }
    // Если login удался, но commit не был (или не будет) вызван, очищаем состояние
    clearState();
    loginSucceeded = false; // Явно сбрасываем
    return true;
  }

  @Override
  public boolean logout() throws LoginException {
    log.debug(
      "Logout called for user: {}",
      authenticatedUsername != null ? authenticatedUsername : "<unknown>"
    );
    if (subject.isReadOnly()) {
      throw new LoginException(
        "Subject is Readonly, cannot remove Principals."
      );
    }

    Set<Principal> toRemoveFromSubject = new HashSet<>();
    if (authenticatedUsername != null) { // Если мы знаем, кто залогинился через этот модуль
      subject
        .getPrincipals(UserPrincipalImpl.class)
        .stream()
        .filter(p -> authenticatedUsername.equals(p.getName()))
        .forEach(toRemoveFromSubject::add);
      subject
        .getPrincipals(RolePrincipal.class)
        .forEach(toRemoveFromSubject::add);
    } else if (!principalsToAdd.isEmpty()) {
      // Если logout до commit или в abort, и principalsToAdd не пуст
      toRemoveFromSubject.addAll(principalsToAdd);
    }

    if (!toRemoveFromSubject.isEmpty()) {
      subject.getPrincipals().removeAll(toRemoveFromSubject);
      log.debug(
        "Principals removed from subject during logout: {}",
        toRemoveFromSubject
      );
    }

    clearState();
    loginSucceeded = false;
    return true;
  }

  private void clearState() {
    authenticatedUsername = null;
    principalsToAdd.clear();
    // loginSucceeded сбрасывается в login() или abort()
  }
}
