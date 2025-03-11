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

@RestController
@RequestMapping("/api/transfers")
@Slf4j
public class TransferController {

    private final TransferService transferService;

    @Autowired
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

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

    @GetMapping("/{transferId}")
    public ResponseEntity<Transfer> getTransferStatus(
            @PathVariable UUID transferId,
            @RequestHeader("X-Phone-Number") String senderPhoneNumber) {
            
        Transfer transfer = transferService.getTransferStatus(transferId);
        
        if (!transfer.getSenderPhoneNumber().equals(senderPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(transfer);
    }
}