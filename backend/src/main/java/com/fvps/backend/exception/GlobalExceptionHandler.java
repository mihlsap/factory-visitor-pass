package com.fvps.backend.exception;

import com.fvps.backend.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler for the entire application.
 * <p>
 * This class uses {@link RestControllerAdvice} to intercept exceptions thrown
 * by any controller.
 * It transforms raw Java exceptions into standardised JSON responses and
 * ensures that
 * all significant error events (validation failures, conflicts, crashes) are
 * recorded
 * in the audit log for security monitoring and debugging.
 * </p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditLogService auditLogService;

    /**
     * Handles validation errors when {@code @Valid} checks fail on DTOs.
     * <p>
     * Collects all field errors into a single message string and returns a 400 Bad
     * Request.
     * Logs the event as "VALIDATION_ERROR" to track potentially malicious input.
     * </p>
     *
     * @param ex the exception containing validation results.
     * @return a map containing the aggregated error message with HTTP 400 status.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        try {
            auditLogService.logEvent("VALIDATION_ERROR", "Input validation failed: " + errors);
        } catch (Exception e) {
            log.error("Failed to save audit log for validation error", e);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Handles optimistic locking conflicts (concurrent modification).
     * <p>
     * Occurs when a user tries to update an entity (e.g. Training, User) that has
     * been modified
     * by another process since it was loaded. Returns HTTP 409 Conflict.
     * </p>
     *
     * @param ex        the locking failure exception.
     * @param principal the currently authenticated user (if any).
     * @return a map containing the error message with HTTP 409 status.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex,
            Principal principal) {
        try {
            String userEmail = principal != null ? principal.getName() : "UNKNOWN";
            auditLogService.logEvent("VERSION_ERROR",
                    "User: " + userEmail + " encountered version conflict: " + ex.getMessage());
        } catch (Exception e) {
            log.error("Failed to save audit log for version error", e);
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles generic runtime exceptions (business logic violations).
     * <p>
     * Catches unchecked exceptions thrown explicitly by services (e.g. "User
     * already exists").
     * Returns HTTP 400 Bad Request.
     * </p>
     *
     * @param ex the runtime exception.
     * @return a map containing the error message with HTTP 400 status.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        try {
            auditLogService.logEvent("LOGIC_ERROR", "Bad request: " + ex.getMessage());
        } catch (Exception e) {
            log.error("Failed to save audit log for logic error", e);
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Fallback handler for unexpected system exceptions.
     * <p>
     * Catches any unhandled exception (NullPointerException, database connection
     * failure, etc.)
     * to prevent exposing stack traces to the client. Returns HTTP 500 Internal
     * Server Error
     * and logs a critical system error.
     * </p>
     *
     * @param ex the unexpected exception.
     * @return a map containing a generic error message with HTTP 500 status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        try {
            auditLogService.logEvent("SYSTEM_CRITICAL_ERROR", "Unexpected exception: " + ex.getMessage());
        } catch (Exception e) {
            log.error("Failed to save audit log for system critical error", e);
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}