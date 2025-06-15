package sbp.transfer.controller;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sbp.dto.rest.ConfirmTransferRequest;
import sbp.dto.rest.ErrorResponse;
import sbp.dto.rest.InitiateTransferRequest;
import sbp.dto.rest.LoginRequest;
import sbp.dto.rest.LoginResponse;
import sbp.dto.rest.TransferConfirmationResponse;
import sbp.dto.rest.TransferInitiationResponse;
import sbp.transfer.entity.Transfer;
import sbp.transfer.exception.TransferNotFoundException;
import sbp.transfer.repository.TransferRepository;
import sbp.transfer.service.TransferOrchestrationService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

  private final TransferOrchestrationService transferOrchestrationService;
  private final AuthenticationManager authenticationManager;
  private final TransferRepository transferRepository;

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(
    @Valid @RequestBody LoginRequest loginRequest
  ) {
    try {
      log.info("Login attempt for user: {}", loginRequest.getUsername());
      UsernamePasswordAuthenticationToken authToken =
        new UsernamePasswordAuthenticationToken(
          loginRequest.getUsername(),
          loginRequest.getPassword()
        );
      Authentication authentication = authenticationManager.authenticate(
        authToken
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);

      String username = authentication.getName();
      List<String> roles = authentication
        .getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

      log.info(
        "User {} logged in successfully with roles: {}",
        username,
        roles
      );
      return ResponseEntity.ok(
        new LoginResponse("Login successful", username, roles)
      );
    } catch (BadCredentialsException e) {
      log.warn(
        "Login failed for user {}: Invalid credentials",
        loginRequest.getUsername()
      );
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
        new ErrorResponse(
          Instant.now(),
          HttpStatus.UNAUTHORIZED.value(),
          "Unauthorized",
          "Invalid username or password",
          "/api/v1/auth/login"
        )
      );
    } catch (Exception e) {
      log.error(
        "Error during login for user {}",
        loginRequest.getUsername(),
        e
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        new ErrorResponse(
          Instant.now(),
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Internal Server Error",
          "Login error: " +
          e.getClass().getSimpleName() +
          " - " +
          e.getMessage(),
          "/api/v1/auth/login"
        )
      );
    }
  }

  @PostMapping("/transfers/initiate")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<?> initiateTransfer(
    Authentication authentication,
    @Valid @RequestBody InitiateTransferRequest request
  ) {
    String senderPhoneNumber = authentication.getName();
    UUID correlationId = UUID.randomUUID();

    log.info(
      "User '{}' initiating transfer (corrId: {}): {}",
      senderPhoneNumber,
      correlationId,
      request
    );

    try {
      TransferInitiationResponse response =
        transferOrchestrationService.initiateTransfer(
          correlationId,
          senderPhoneNumber,
          request
        );
      // При успешной инициации возвращаем 202 Accepted с телом ответа
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    } catch (IllegalArgumentException e) {
      log.warn(
        "Failed to initiate transfer for corrId {}: {}",
        correlationId,
        e.getMessage()
      );
      ErrorResponse errorResponse = new ErrorResponse(
        Instant.now(),
        HttpStatus.BAD_REQUEST.value(),
        "Bad Request",
        e.getMessage(),
        "/api/v1/transfers/initiate"
      );
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    } catch (RuntimeException e) {
      log.error(
        "Failed to initiate transfer for corrId {}: {}",
        correlationId,
        e.getMessage(),
        e
      );
      ErrorResponse errorResponse = new ErrorResponse(
        Instant.now(),
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "Internal Server Error",
        "Failed to initiate transfer: " + e.getMessage(),
        "/api/v1/transfers/initiate"
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        errorResponse
      );
    }
  }

  @PostMapping("/transfers/{transferId}/confirm")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<?> confirmTransfer(
    @PathVariable UUID transferId,
    @Valid @RequestBody ConfirmTransferRequest confirmRequest,
    Authentication authentication
  ) {
    String authenticatedUser = authentication.getName();
    String maskedCode =
      "****" +
      (confirmRequest.getConfirmationCode().length() > 3
          ? confirmRequest
            .getConfirmationCode()
            .substring(confirmRequest.getConfirmationCode().length() - 2)
          : "");

    log.info(
      "User '{}' confirming transferId: {} with code: {}",
      authenticatedUser,
      transferId,
      maskedCode
    );

    try {
      TransferConfirmationResponse response =
        transferOrchestrationService.confirmTransfer(
          transferId,
          confirmRequest.getConfirmationCode(),
          authenticatedUser
        );
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.warn("Bad request for transferId {}: {}", transferId, e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
        new ErrorResponse(
          Instant.now(),
          HttpStatus.BAD_REQUEST.value(),
          "Bad Request",
          e.getMessage(),
          "/api/v1/transfers/" + transferId + "/confirm"
        )
      );
    } catch (SecurityException e) {
      log.warn(
        "Forbidden access for transferId {}: {}",
        transferId,
        e.getMessage()
      );
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
        new ErrorResponse(
          Instant.now(),
          HttpStatus.FORBIDDEN.value(),
          "Forbidden",
          e.getMessage(),
          "/api/v1/transfers/" + transferId + "/confirm"
        )
      );
    } catch (RuntimeException e) {
      log.error(
        "Error confirming transferId {}: {}",
        transferId,
        e.getMessage(),
        e
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        new ErrorResponse(
          Instant.now(),
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Internal Server Error",
          "Failed to confirm transfer: " + e.getMessage(),
          "/api/v1/transfers/" + transferId + "/confirm"
        )
      );
    }
    // Можно добавить catch (Exception e) для самых общих ошибок
  }

  @GetMapping("/transfers/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> getTransferDetails(
    // Изменено на ResponseEntity<?>
    @PathVariable UUID id,
    Authentication authentication
  ) {
    log.info(
      "Admin user '{}' requesting details for transfer with PK ID: {}",
      authentication.getName(),
      id
    );
    try {
      Transfer transfer = transferRepository
        .findById(id)
        .orElseThrow(() ->
          new TransferNotFoundException("Transfer not found with ID: " + id)
        );
      log.debug("Returning transfer details for PK ID: {}", id);
      return ResponseEntity.ok(transfer);
    } catch (TransferNotFoundException e) {
      log.warn(
        "Admin requested non-existent transfer with PK ID: {}. Error: {}",
        id,
        e.getMessage()
      );
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        new ErrorResponse(
          Instant.now(),
          HttpStatus.NOT_FOUND.value(),
          "Not Found",
          e.getMessage(),
          "/api/v1/transfers/" + id
        )
      );
    }
    // Можно добавить catch (Exception e)
  }
}
