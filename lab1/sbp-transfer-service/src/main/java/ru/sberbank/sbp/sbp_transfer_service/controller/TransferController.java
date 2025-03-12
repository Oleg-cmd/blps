package ru.sberbank.sbp.sbp_transfer_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sberbank.sbp.sbp_transfer_service.entity.Transfer;
import ru.sberbank.sbp.sbp_transfer_service.service.TransferService;
import ru.sberbank.sbp.sbp_transfer_service.dto.InitiateTransferRequest;
import ru.sberbank.sbp.sbp_transfer_service.dto.ConfirmTransferRequest;
import ru.sberbank.sbp.sbp_transfer_service.dto.TransferInitiationResponse;
import ru.sberbank.sbp.sbp_transfer_service.dto.TransferConfirmationResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
@Slf4j
public class TransferController {

    private final TransferService transferService;

    @Autowired
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Эндпоинт для создания нового перевода
     * Требует номер телефона отправителя в заголовке X-Phone-Number
     */
    @PostMapping
    public ResponseEntity<TransferInitiationResponse> initiateTransfer(
            @Valid @RequestBody InitiateTransferRequest request,
            @RequestHeader("X-Phone-Number") String senderPhoneNumber) {
        
        log.info("Received transfer request from {}", senderPhoneNumber);
        TransferInitiationResponse response = transferService.initiateTransfer(
            senderPhoneNumber,
            request.getRecipientPhoneNumber(),
            request.getAmount(),
            request.getBankId()
        );
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Эндпоинт для подтверждения перевода
     * Принимает код подтверждения и ID перевода
     */
    @PostMapping("/{transferId}/confirm")
    public ResponseEntity<TransferConfirmationResponse> confirmTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody ConfirmTransferRequest request,
            @RequestHeader("X-Phone-Number") String senderPhoneNumber) {
            
        log.info("Received confirmation request from {}", senderPhoneNumber);
        TransferConfirmationResponse response = transferService.confirmTransfer(
            transferId,
            request.getConfirmationCode()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Эндпоинт для проверки статуса перевода
     * Доступен только отправителю перевода
     */
    @GetMapping("/{transferId}")
    public ResponseEntity<Object> getTransferStatus( // Изменили ResponseEntity<Transfer> на ResponseEntity<Object>
            @PathVariable UUID transferId,
            @RequestHeader("X-Phone-Number") String senderPhoneNumber) {

        log.info("getTransferStatus: Received status request for transferId={}, senderPhoneNumber={}", transferId, senderPhoneNumber);

        Transfer transfer = transferService.getTransferStatus(transferId);

        if (transfer == null) {
            log.warn("getTransferStatus: Transfer not found for transferId={}", transferId);
            return ResponseEntity.notFound().build();
        }

        if (!transfer.getSenderPhoneNumber().equals(senderPhoneNumber)) {
            log.warn("getTransferStatus: Unauthorized access attempt. Transfer sender={}, request sender={}", transfer.getSenderPhoneNumber(), senderPhoneNumber);
            // Возвращаем JSON с сообщением об ошибке и статусом 403
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Unauthorized access");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        log.info("getTransferStatus: Successfully returned status for transferId={}", transferId);
        return ResponseEntity.ok(transfer);
    }
}