package com.example.demo;

import com.example.demo.services.NotificationDeliveryException;
import com.example.demo.services.NotificationPermanentException;
import com.example.demo.services.ProviderNotConfiguredException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return createErrorResponse(status, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or contains invalid JSON",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                "The request conflicts with an existing record",
                request);
    }

    @ExceptionHandler(ProviderNotConfiguredException.class)
    public ResponseEntity<ApiError> handleProviderNotConfigured(
            ProviderNotConfiguredException exception,
            HttpServletRequest request) {
        log.error("Notification provider is not configured: {}", exception.getMessage());
        return createErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Email delivery is not configured on this server. "
                        + "Add your SMTP credentials to local-secrets.properties, then restart the backend.",
                request);
    }

    @ExceptionHandler({NotificationDeliveryException.class, NotificationPermanentException.class})
    public ResponseEntity<ApiError> handleNotificationDeliveryFailure(
            RuntimeException exception,
            HttpServletRequest request) {
        log.error("Email delivery failed for {}", request.getRequestURI(), exception);
        return createErrorResponse(
                HttpStatus.BAD_GATEWAY,
                "The email could not be delivered. Please try again later.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), exception);
        return createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred",
                request);
    }

    private ResponseEntity<ApiError> createErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
    }
}
