package ru.sberbank.sbp.lab2.transfer_service.dto;

import lombok.Value;

@Value
public class ErrorResponse {

  String timestamp;
  int status;
  String error;
  String message;
  String path;
  // Можно добавить и другие поля по необходимости
}
