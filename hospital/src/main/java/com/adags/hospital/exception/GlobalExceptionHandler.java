package com.adags.hospital.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Applies ONLY to @RestController beans (REST/API endpoints).
// Web MVC @Controller beans (Thymeleaf pages) are excluded so they can
// return HTML redirects instead of JSON bodies.
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
@Slf4j
public class GlobalExceptionHandler {

    // -----------------------------------------------------------------------
    //  400 — Validation errors
    // -----------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                fieldErrors,
                LocalDateTime.now());
        return ResponseEntity.badRequest().body(body);
    }

    // -----------------------------------------------------------------------
    //  404 — Resource not found
    // -----------------------------------------------------------------------
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(simple(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    // -----------------------------------------------------------------------
    //  409 — Duplicate resource
    // -----------------------------------------------------------------------
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(simple(HttpStatus.CONFLICT, ex.getMessage()));
    }

    // -----------------------------------------------------------------------
    //  422 — Business rule violation
    // -----------------------------------------------------------------------
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(simple(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()));
    }

    // -----------------------------------------------------------------------
    //  401 — Bad credentials
    // -----------------------------------------------------------------------
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(simple(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
    }

    // -----------------------------------------------------------------------
    //  403 — Access denied
    // -----------------------------------------------------------------------
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(simple(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"));
    }

    // -----------------------------------------------------------------------
    //  500 — Catch-all
    // -----------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}: {}", request.getDescription(false), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(simple(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later."));
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------
    private ErrorResponse simple(HttpStatus status, String message) {
        return new ErrorResponse(status.value(), message, null, LocalDateTime.now());
    }

    public record ErrorResponse(
            int status,
            String message,
            Map<String, String> fieldErrors,
            LocalDateTime timestamp
    ) {}
}
