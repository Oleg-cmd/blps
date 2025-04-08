package ru.sberbank.sbp.lab2.transfer_service.service.stub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sberbank.sbp.lab2.transfer_service.service.PhoneValidationService;

@Service
@Slf4j
public class PhoneValidationServiceStub implements PhoneValidationService {

  private static final String PHONE_REGEX = "\\d{10}";

  @Override
  public boolean validatePhoneFormat(String phoneNumber) {
    boolean isValid = false;
    if (phoneNumber != null) {
      isValid = phoneNumber.matches(PHONE_REGEX);
    }
    log.debug(
      "[PhoneValidationServiceStub] Validating phone format for '{}': {}",
      phoneNumber,
      isValid
    );
    return isValid;
  }
}
