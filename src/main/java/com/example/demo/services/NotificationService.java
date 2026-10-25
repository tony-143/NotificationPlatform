package com.example.demo.services;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.repositeries.ApiKeyRepository;
import com.example.demo.repositeries.NotificationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final ApiKeyRepository apiKeyRepository;
    private final NotificationRepository notificationRepository;
    private final IdempotencyChecker idempotencyChecker;
    private final NotificationRequestValidator requestValidator;
    private final NotificationRequestHasher requestHasher;

    public NotificationService(
            ApiKeyRepository apiKeyRepository,
            NotificationRepository notificationRepository,
            IdempotencyChecker idempotencyChecker,
            NotificationRequestValidator requestValidator,
            NotificationRequestHasher requestHasher) {
        this.apiKeyRepository = apiKeyRepository;
        this.notificationRepository = notificationRepository;
        this.idempotencyChecker = idempotencyChecker;
        this.requestValidator = requestValidator;
        this.requestHasher = requestHasher;
    }

    @Transactional
    public NotificationOutcome submit(Long apiKeyId, String idempotencyKey, NotificationRequest request) {
        validateIdempotencyKey(idempotencyKey);
        NotificationRequestValidator.ValidatedRequest validated = requestValidator.validate(request);

        ApiKeys apiKey = apiKeyRepository.findByIdForUpdate(apiKeyId)
                .filter(key -> ApiKeys.ACTIVE.equals(key.getKeyStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked API key"));
        Instant now = Instant.now();
        String requestHash = requestHasher.hash(validated);

        var existing = idempotencyChecker.findExisting(apiKey, idempotencyKey, requestHash, now);
        if (existing.isPresent()) {
            return outcome(existing.get(), true);
        }

        Notification notification = new Notification(
                apiKey.getUser(),
                validated.type(),
                validated.recipient(),
                validated.message(),
                validated.payload(),
                validated.scheduledAt());

        Notification saved = notificationRepository.saveAndFlush(notification);
        idempotencyChecker.save(apiKey, idempotencyKey, requestHash, saved, now);
        return outcomeFromNotification(saved, false);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> getNotifications(Long apiKeyId) {
        ApiKeys apiKey = apiKeyRepository.findById(apiKeyId)
                .filter(key -> ApiKeys.ACTIVE.equals(key.getKeyStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked API key"));
        return notificationRepository.findByTenant_IdOrderByCreatedAtDesc(apiKey.getUser().getId())
                .stream()
                .map(NotificationView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationView getNotification(Long apiKeyId, Long notificationId) {
        ApiKeys apiKey = apiKeyRepository.findById(apiKeyId)
                .filter(key -> ApiKeys.ACTIVE.equals(key.getKeyStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked API key"));
        return notificationRepository.findByIdAndTenant_Id(notificationId, apiKey.getUser().getId())
                .map(NotificationView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private NotificationOutcome outcome(IdempotencyRecord record, boolean duplicate) {
        return outcomeFromNotification(record.getNotification(), duplicate);
    }

    private NotificationOutcome outcomeFromNotification(Notification notification, boolean duplicate) {
        int httpStatus = duplicate ? HttpStatus.OK.value() : HttpStatus.ACCEPTED.value();
        return new NotificationOutcome(NotificationView.from(notification), duplicate, httpStatus);
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required and must be at most 200 characters");
        }
    }

    public record NotificationRequest(String type, String recipient, String message, String payload, Instant scheduledAt) {
        public NotificationRequest(String type, String recipient, String message, String payload) {
            this(type, recipient, message, payload, null);
        }
    }

    public record NotificationOutcome(NotificationView notification, boolean duplicate, int httpStatus) {
    }

    public record NotificationView(
            Long id,
            Long tenantId,
            String type,
            String recipient,
            String message,
            String payload,
            NotificationStatus status,
            String statusMessage,
            int retryCount,
            String nextAttemptAt,
            String closedAt,
            String scheduledAt,
            String createdAt,
            String updatedAt) {

        private static NotificationView from(Notification notification) {
            return new NotificationView(
                    notification.getId(),
                    notification.getTenant().getId(),
                    notification.getNotificationType(),
                    notification.getRecipient(),
                    notification.getMessage(),
                    notification.getPayload(),
                    notification.getStatus(),
                    notification.getStatusMessage(),
                    notification.getRetryCount(),
                    notification.getNextAttemptAt() == null ? null : notification.getNextAttemptAt().toString(),
                    notification.getClosedAt() == null ? null : notification.getClosedAt().toString(),
                    notification.getScheduledAt() == null ? null : notification.getScheduledAt().toString(),
                    notification.getCreatedAt().toString(),
                    notification.getUpdatedAt().toString());
        }
    }
}
