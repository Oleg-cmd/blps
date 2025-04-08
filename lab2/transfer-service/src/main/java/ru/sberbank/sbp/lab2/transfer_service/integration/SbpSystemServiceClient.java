package ru.sberbank.sbp.lab2.transfer_service.integration;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.sberbank.sbp.lab2.transfer_service.entity.Transfer;
import ru.sberbank.sbp.lab2.transfer_service.integration.models.BankInfo;
import ru.sberbank.sbp.lab2.transfer_service.integration.models.SbpTransferRequestDto;
import ru.sberbank.sbp.lab2.transfer_service.integration.models.SbpTransferResponse;
import ru.sberbank.sbp.lab2.transfer_service.service.SbpSystemService; // Импортируем интерфейс

@Service
@Slf4j
public class SbpSystemServiceClient implements SbpSystemService {

  private final RestTemplate restTemplate;
  private final String sbpAdapterBaseUrl;

  // Внедряем RestTemplate и базовый URL из конфигурации
  public SbpSystemServiceClient(
    @Qualifier("sbpAdapterRestTemplate") RestTemplate restTemplate, // Используем квалификатор
    @Value("${integration.sbp-adapter.base-url}") String sbpAdapterBaseUrl
  ) {
    this.restTemplate = restTemplate;
    this.sbpAdapterBaseUrl = sbpAdapterBaseUrl;
  }

  @Override
  public Optional<BankInfo> findRecipientBank(String phoneNumber) {
    String url = UriComponentsBuilder.fromHttpUrl(sbpAdapterBaseUrl)
      .path("/api/sbp/banks")
      .queryParam("phoneNumber", phoneNumber)
      .toUriString();

    log.debug("Calling SBP Adapter to find bank: {}", url);
    try {
      ResponseEntity<BankInfo> response = restTemplate.getForEntity(
        url,
        BankInfo.class
      );
      if (
        response.getStatusCode().is2xxSuccessful() && response.getBody() != null
      ) {
        log.info(
          "SBP Adapter found bank for phone {}: {}",
          phoneNumber,
          response.getBody()
        );
        return Optional.of(response.getBody());
      } else {
        // Это не должно происходить при 2xx, но на всякий случай
        log.warn(
          "SBP Adapter returned status {} for find bank request for phone {}",
          response.getStatusCode(),
          phoneNumber
        );
        return Optional.empty();
      }
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatusCode.valueOf(404)) {
        log.warn(
          "SBP Adapter returned 404 Not Found for phone {}",
          phoneNumber
        );
        return Optional.empty(); // Банк не найден
      } else {
        log.error(
          "SBP Adapter client error on find bank for phone {}: Status={}, Body={}",
          phoneNumber,
          e.getStatusCode(),
          e.getResponseBodyAsString(),
          e
        );
        // Бросаем исключение или возвращаем empty? Зависит от политики.
        // Пока вернем empty, чтобы не прерывать, но логируем как ERROR.
        return Optional.empty();
      }
    } catch (RestClientException e) {
      log.error(
        "SBP Adapter connection error on find bank for phone {}: {}",
        phoneNumber,
        e.getMessage(),
        e
      );
      // Ошибка сети - возвращаем empty
      return Optional.empty();
    }
  }

  @Override
  public SbpTransferResponse processTransferViaSbp(Transfer transfer) {
    String url = UriComponentsBuilder.fromHttpUrl(sbpAdapterBaseUrl)
      .path("/api/sbp/transfers")
      .toUriString();

    SbpTransferRequestDto requestDto = SbpTransferRequestDto.builder()
      .senderPhoneNumber(transfer.getSenderPhoneNumber())
      .recipientPhoneNumber(transfer.getRecipientPhoneNumber())
      .amount(transfer.getAmount())
      .correlationId(transfer.getId())
      .build();

    log.debug(
      "Calling SBP Adapter to process transfer {}: {}",
      transfer.getId(),
      url
    );
    try {
      ResponseEntity<SbpTransferResponse> response = restTemplate.postForEntity(
        url,
        requestDto,
        SbpTransferResponse.class
      );

      if (
        response.getStatusCode().is2xxSuccessful() && response.getBody() != null
      ) {
        SbpTransferResponse sbpResponse = response.getBody();
        log.info(
          "SBP Adapter returned result for transfer {}: success={}, message={}",
          transfer.getId(),
          sbpResponse.isSuccess(),
          sbpResponse.getErrorMessage()
        );
        return sbpResponse;
      } else {
        log.error(
          "SBP Adapter returned unexpected status {} for process transfer request for id {}",
          response.getStatusCode(),
          transfer.getId()
        );
        // Возвращаем неуспех, если статус не 2xx
        return SbpTransferResponse.builder()
          .success(false)
          .errorMessage(
            "SBP Adapter communication error: status " +
            response.getStatusCode()
          )
          .build();
      }
    } catch (RestClientException e) {
      log.error(
        "SBP Adapter connection error on process transfer for id {}: {}",
        transfer.getId(),
        e.getMessage(),
        e
      );
      // Возвращаем неуспех при ошибке сети
      return SbpTransferResponse.builder()
        .success(false)
        .errorMessage("SBP Adapter connection error: " + e.getMessage())
        .build();
    }
  }
}
