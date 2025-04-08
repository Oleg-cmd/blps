package ru.sberbank.sbp.lab2.transfer_service.service;

public interface PhoneValidationService {
  /**
   * Проверяет корректность формата номера телефона (например, 10 цифр).
   * @param phoneNumber Номер телефона для проверки.
   * @return true, если формат корректный, иначе false.
   */
  boolean validatePhoneFormat(String phoneNumber);
}
