package com.fvps.backend.exception;

import com.fvps.backend.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditLogService auditLogService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; ")
                );

        try {
            auditLogService.logEvent("VALIDATION_ERROR", "Input validation failed: " + errorMessage);
        } catch (Exception ignored) {
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex,
            Principal principal
    ) {
        try {
            String userEmail = principal != null ? principal.getName() : "UNKNOWN";
            auditLogService.logEvent("VERSION_ERROR", "User: " + userEmail + " encountered version conflict: " + ex.getMessage());
        } catch (Exception ignored) {
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        try {
            auditLogService.logEvent("LOGIC_ERROR", "Bad request: " + ex.getMessage());
        } catch (Exception ignored) {
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        try {
            auditLogService.logEvent("SYSTEM_CRITICAL_ERROR", "Unexpected exception: " + ex.getMessage());
        } catch (Exception ignored) {
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}