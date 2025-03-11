package ru.sberbank.sbp.sbp_transfer_service.service;

import org.springframework.stereotype.Service;

@Service
public interface PhoneValidationService {
    boolean validatePhoneFormat(String phoneNumber);
}