package ru.sberbank.sbp.sbp_transfer_service.service;

public interface PhoneValidationService {
    
    /**
     * Проверяет корректность формата номера телефона
     * @param phoneNumber номер телефона для проверки
     * @return true если формат корректный, false в противном случае
     */
    boolean validatePhoneFormat(String phoneNumber);
    
    /**
     * Форматирует номер телефона к стандартному виду
     * @param phoneNumber исходный номер телефона
     * @return отформатированный номер телефона
     * @throws PhoneFormatException если номер невозможно привести к правильному формату
     */
    String formatPhoneNumber(String phoneNumber);
}