package com.example.demo.controllers;

import com.example.demo.models.ApiKeys;
import com.example.demo.services.NotificationService;
import com.example.demo.services.NotificationService.NotificationOutcome;
import com.example.demo.services.NotificationService.NotificationRequest;
import com.example.demo.services.NotificationService.NotificationView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping({"/api/notifications", "/api/v1/notifications"})
    public List<NotificationView> getNotifications(Authentication authentication) {
        return notificationService.getNotifications(apiKey(authentication).getId());
    }

    @GetMapping({"/api/notifications/{notificationId}", "/api/v1/notifications/{notificationId}"})
    public NotificationView getNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {
        return notificationService.getNotification(apiKey(authentication).getId(), notificationId);
    }

    @PostMapping({"/api/notifications", "/api/v1/notifications"})
    public ResponseEntity<NotificationSubmissionResponse> createNotification(
            Authentication authentication,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody NotificationRequest request) {
        NotificationOutcome outcome = notificationService.submit(
                apiKey(authentication).getId(),
                idempotencyKey,
                request);
        return ResponseEntity.status(outcome.httpStatus())
                .body(new NotificationSubmissionResponse(outcome.notification(), outcome.duplicate()));
    }

    private ApiKeys apiKey(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ApiKeys apiKey) {
            return apiKey;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required");
    }

    public record NotificationSubmissionResponse(NotificationView notification, boolean duplicate) {
    }
}
