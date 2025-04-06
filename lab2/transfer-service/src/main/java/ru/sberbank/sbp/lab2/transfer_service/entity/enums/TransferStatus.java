package ru.sberbank.sbp.lab2.transfer_service.entity.enums;

public enum TransferStatus {
    PENDING,
    PROCESSING,
    AWAITING_CONFIRMATION, // Ожидание ввода кода подтверждения
    SUCCESSFUL,
    FAILED,
    INSUFFICIENT_FUNDS,
    BANK_NOT_FOUND,
    INVALID_CODE,
    CANCELLED,
    PHONE_FORMAT_ERROR,
    BANK_REQUEST_ERROR,
    CODE_VERIFICATION_ERROR,
    BLOCKED
}