package ru.sberbank.sbp.sbp_transfer_service.service.impl;

import org.springframework.stereotype.Service;

@Service
public class PhoneValidationService {
    public boolean validatePhoneFormat(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("\\d{10}");
    }
}