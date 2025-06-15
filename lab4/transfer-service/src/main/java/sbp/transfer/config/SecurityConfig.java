package sbp.transfer.config;

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
import sbp.transfer.security.jaas.RolePrincipal;

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
      if (principal instanceof RolePrincipal rp) {
        if (roles == null) {
          roles = new HashSet<>();
        }
        roles.add(rp.getName());
        log.debug(
          "Granted role '{}' for principal: {}",
          rp.getName(),
          principal.getName()
        );
      }
      return roles != null ? roles : Collections.emptySet();
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

    if (jaasConfigResource != null && jaasConfigResource.exists()) {
      provider.setLoginConfig(jaasConfigResource);
      log.info("JAAS loginConfig set to: {}", jaasConfigResource);
    } else {
      String errorMessage =
        "JAAS config resource specified by 'java.security.auth.login.config' property ('" +
        (jaasConfigResource != null
            ? jaasConfigResource.getDescription()
            : "null") +
        "') does not exist. JAAS Authentication will likely fail.";
      log.error(errorMessage);
    }

    try {
      provider.afterPropertiesSet();
    } catch (Exception e) {
      log.error("Failed to initialize JaasAuthenticationProvider", e);
      throw new RuntimeException(
        "Failed to initialize JaasAuthenticationProvider",
        e
      );
    }
    log.info("JaasAuthenticationProvider configured successfully.");
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
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/v1/auth/login"
          )
          .permitAll()
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/v1/auth/register"
          )
          .permitAll()
          .requestMatchers(
            org.springframework.http.HttpMethod.GET,
            "/api/v1/transfers/{id:[0-9a-fA-F\\-]+}"
          )
          .hasRole("ADMIN")
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/v1/transfers/initiate"
          )
          .hasRole("USER")
          .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/v1/transfers/{id:[0-9a-fA-F\\-]+}/confirm"
          )
          .hasRole("USER")
          .anyRequest()
          .authenticated()
      );
    return http.build();
  }
}
