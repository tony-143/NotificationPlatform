package com.example.demo.services;

import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NotificationRequestValidator {

    public ValidatedRequest validate(NotificationService.NotificationRequest request) {
        if (request == null || request.type() == null || request.type().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification type is required");
        }
        if (request.recipient() == null || request.recipient().isBlank() || request.recipient().length() > 512) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recipient is required and must be at most 512 characters");
        }
        if (request.message() == null || request.message().isBlank() || request.message().length() > 10000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message is required and must be at most 10000 characters");
        }
        if (request.payload() != null && request.payload().length() > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload must be at most 100000 characters");
        }
        if (request.scheduledAt() != null && request.scheduledAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt cannot be in the past");
        }

        String type = request.type().trim().toUpperCase(Locale.ROOT);
        if (!NotificationType.EMAIL.equals(type) && !NotificationType.SMS.equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification type must be EMAIL or SMS");
        }

        return new ValidatedRequest(
                type,
                request.recipient().trim(),
                request.message(),
                request.payload(),
                request.scheduledAt());
    }

    public record ValidatedRequest(
            String type,
            String recipient,
            String message,
            String payload,
            Instant scheduledAt) {
    }
}
