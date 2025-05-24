package sbp.transfer.integration;

import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import sbp.dto.rest.BankInfo;
import sbp.transfer.exception.SbpAdapterException;

@Component
@Slf4j
public class SbpAdapterClient {

  private final RestTemplate restTemplate;
  private final String sbpAdapterBanksApiUrl;

  public SbpAdapterClient(
    RestTemplateBuilder restTemplateBuilder,
    @Value("${sbp.adapter.base-url}") String sbpAdapterBaseUrl,
    @Value("${sbp.adapter.api.banks.path:/api/sbp/banks}") String banksApiPath,
    @Value("${sbp.adapter.timeout.connect:2000}") int connectTimeout,
    @Value("${sbp.adapter.timeout.read:5000}") int readTimeout
  ) {
    this.restTemplate = restTemplateBuilder
      .setConnectTimeout(Duration.ofMillis(connectTimeout))
      .setReadTimeout(Duration.ofMillis(readTimeout))
      .build();
    this.sbpAdapterBanksApiUrl = sbpAdapterBaseUrl + banksApiPath;
  }

  /**
   * Запрашивает информацию о банке по его идентификатору (bankId) у SBP Adapter.
   *
   * @param bankId Идентификатор банка (например, BIC).
   * @return Optional<BankInfo>, содержащий информацию о банке, если запрос успешен и банк найден.
   *         Пустой Optional в случае, если банк не найден (404) или произошла ошибка связи/сервера.
   * @throws SbpAdapterException если произошла критическая ошибка при взаимодействии с адаптером,
   *                             которую невозможно обработать как "не найдено".
   */
  public Optional<BankInfo> getBankInfoById(String bankId) {
    String url = sbpAdapterBanksApiUrl + "/" + bankId; // Формируем полный URL для конкретного bankId
    log.debug(
      "Requesting bank info from SBP Adapter. URL: {}, bankId: {}",
      url,
      bankId
    );

    try {
      ResponseEntity<BankInfo> response = restTemplate.getForEntity(
        url,
        BankInfo.class
      );

      if (
        response.getStatusCode() == HttpStatus.OK && response.getBody() != null
      ) {
        BankInfo bankInfo = response.getBody();
        log.info(
          "Successfully retrieved bank info for bankId {}: Name='{}', SupportsSBP={}",
          bankId,
          bankInfo.getBankName(),
          bankInfo.isSupportsSbp()
        );
        return Optional.of(bankInfo);
      } else {
        log.warn(
          "Received non-OK status ({}) or empty body from SBP Adapter for bankId {}",
          response.getStatusCode(),
          bankId
        );
        return Optional.empty(); // Не ОК или пустое тело - считаем, что информации нет
      }
    } catch (HttpClientErrorException.NotFound e) {
      log.warn(
        "Bank with ID {} not found in SBP Adapter (404). URL: {}",
        bankId,
        url
      );
      return Optional.empty(); // Банк не найден
    } catch (HttpClientErrorException e) { // Другие 4xx ошибки
      log.error(
        "Client error calling SBP Adapter for bankId {}. Status: {}, URL: {}, Response: {}",
        bankId,
        e.getStatusCode(),
        url,
        e.getResponseBodyAsString(),
        e
      );
      throw new SbpAdapterException(
        "SBP Adapter client error: " +
        e.getStatusCode() +
        " for bankId " +
        bankId,
        e
      );
    } catch (HttpServerErrorException e) { // 5xx ошибки
      log.error(
        "Server error from SBP Adapter for bankId {}. Status: {}, URL: {}, Response: {}",
        bankId,
        e.getStatusCode(),
        url,
        e.getResponseBodyAsString(),
        e
      );
      throw new SbpAdapterException(
        "SBP Adapter server error: " +
        e.getStatusCode() +
        " for bankId " +
        bankId,
        e
      );
    } catch (ResourceAccessException e) { // Ошибки соединения, таймауты
      log.error(
        "Could not connect to SBP Adapter for bankId {}. URL: {}, Error: {}",
        bankId,
        url,
        e.getMessage(),
        e
      );
      throw new SbpAdapterException(
        "SBP Adapter connection failed for bankId " + bankId,
        e
      );
    } catch (RestClientException e) { // Общий случай для других ошибок RestTemplate
      log.error(
        "Unexpected RestClientException calling SBP Adapter for bankId {}. URL: {}, Error: {}",
        bankId,
        url,
        e.getMessage(),
        e
      );
      throw new SbpAdapterException(
        "Unexpected error communicating with SBP Adapter for bankId " + bankId,
        e
      );
    }
  }
}
