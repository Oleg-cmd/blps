package ru.sberbank.sbp.lab2.transfer_service.config; // Правильный пакет

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Для отключения CSRF
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // Включаем веб-безопасность Spring
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http)
    throws Exception {
    http
      // 1. Отключаем CSRF (т.к. у нас будет stateless API с HTTP Basic/без кук)
      .csrf(AbstractHttpConfigurer::disable)
      // 2. Разрешаем все запросы к /api/** БЕЗ АУТЕНТИФИКАЦИИ (ВРЕМЕННО!)
      .authorizeHttpRequests(
        auth ->
          auth
            .requestMatchers("/api/**")
            .permitAll() // Разрешить все к нашему API
            .anyRequest()
            .authenticated() // Все остальные запросы требуют аутентификации (например, к Actuator)
      )
      // 3. Оставляем HTTP Basic включенным по умолчанию (или форму входа)
      // Позже мы настроим его для JAAS
      .httpBasic(withDefaults()); // Включить HTTP Basic с настройками по умолчанию
    // .formLogin(withDefaults()); // Или форму входа

    return http.build();
  }
  // TODO: На Этапе 4 мы заменим .permitAll() на правила с ролями
  // TODO: и настроим JaasAuthenticationProvider
}
