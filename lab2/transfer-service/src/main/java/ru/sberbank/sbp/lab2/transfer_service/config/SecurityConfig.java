package ru.sberbank.sbp.lab2.transfer_service.config;

// Убран импорт javax.security.auth.login.Configuration
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.jaas.AuthorityGranter;
import org.springframework.security.authentication.jaas.JaasAuthenticationCallbackHandler;
import org.springframework.security.authentication.jaas.JaasAuthenticationProvider;
import org.springframework.security.authentication.jaas.JaasNameCallbackHandler;
import org.springframework.security.authentication.jaas.JaasPasswordCallbackHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.sberbank.sbp.lab2.transfer_service.security.jaas.RolePrincipal;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(
    SecurityConfig.class
  );

  @Value("${java.security.auth.login.config:classpath:jaas.conf}")
  private Resource jaasConfigResource;

  private static final String JAAS_LOGIN_CONTEXT_NAME = "SbpLogin";

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthorityGranter authorityGranter() {
    return (Principal principal) -> {
      Set<String> roles = null;
      if (principal instanceof RolePrincipal) {
        if (roles == null) {
          roles = new HashSet<>();
        }
        roles.add(principal.getName());
      }
      return roles;
    };
  }

  @Bean
  public JaasAuthenticationCallbackHandler jaasNameCallbackHandler() {
    return new JaasNameCallbackHandler();
  }

  @Bean
  public JaasAuthenticationCallbackHandler jaasPasswordCallbackHandler() {
    return new JaasPasswordCallbackHandler();
  }

  @Bean
  public JaasAuthenticationProvider jaasAuthenticationProvider(
    AuthorityGranter authorityGranter,
    JaasAuthenticationCallbackHandler jaasNameCallbackHandler,
    JaasAuthenticationCallbackHandler jaasPasswordCallbackHandler
  ) {
    JaasAuthenticationProvider provider = new JaasAuthenticationProvider();
    provider.setAuthorityGranters(new AuthorityGranter[] { authorityGranter });
    provider.setCallbackHandlers(
      new JaasAuthenticationCallbackHandler[] {
        jaasNameCallbackHandler,
        jaasPasswordCallbackHandler,
      }
    );
    provider.setLoginContextName(JAAS_LOGIN_CONTEXT_NAME);

    provider.setLoginConfig(jaasConfigResource);
    log.info(
      "Setting loginConfig for JaasAuthenticationProvider to: {}",
      jaasConfigResource
    ); // Добавим лог

    try {
      provider.afterPropertiesSet();
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to initialize JaasAuthenticationProvider",
        e
      );
    }

    return provider;
  }

  @Bean
  public AuthenticationManager authenticationManager(
    JaasAuthenticationProvider jaasAuthenticationProvider
  ) {
    return new ProviderManager(
      Collections.singletonList(jaasAuthenticationProvider)
    );
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    AuthenticationManager authenticationManager
  ) throws Exception {
    http
      .csrf(AbstractHttpConfigurer::disable)
      .authenticationManager(authenticationManager)
      .httpBasic(Customizer.withDefaults())
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(auth ->
        auth
          // 2. Эндпоинт получения статуса (GET /api/transfers/{id}) - только ADMIN
          // Используем HttpMethod для конкретизации
          .requestMatchers(
            org.springframework.http.HttpMethod.GET,
            "/api/transfers/*"
          ) // Используем * как placeholder для ID
          .hasRole("ADMIN") // <-- Требуем роль ADMIN
          // 3. Эндпоинт инициации (POST /api/transfers) - только USER
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/transfers"
          )
          .hasRole("USER") // <-- Требуем роль USER
          // 4. Эндпоинт подтверждения (POST /api/transfers/{id}/confirm) - только USER
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/transfers/*/confirm"
          ) // Используем * для ID
          .hasRole("USER") // <-- Требуем роль USER
          .anyRequest()
          .authenticated()
      );

    return http.build();
  }
}
