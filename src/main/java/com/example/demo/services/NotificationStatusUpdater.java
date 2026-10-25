package com.example.demo.services;

import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.repositeries.NotificationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationStatusUpdater {

    private static final List<NotificationStatus> DUE_STATUSES =
            List.of(NotificationStatus.PENDING, NotificationStatus.RETRYING);

    private final NotificationRepository notificationRepository;

    public NotificationStatusUpdater(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification claimForProcessing(Long notificationId, Instant now, int maximumRetries) {
        int claimed = notificationRepository.claimForProcessing(
                notificationId,
                DUE_STATUSES,
                NotificationStatus.PENDING,
                maximumRetries,
                NotificationStatus.PROCESSING,
                "Notification is being processed",
                now);
        if (claimed == 0) {
            return null;
        }
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @Transactional
    public void markSent(Long notificationId) {
        Notification notification = findNotification(notificationId);
        notification.setStatus(NotificationStatus.SENT);
        notification.setStatusMessage("Provider accepted notification");
        notification.setNextAttemptAt(null);
    }

    @Transactional
    public void markProviderNotConfigured(Long notificationId, String message) {
        Notification notification = findNotification(notificationId);
        notification.setStatus(NotificationStatus.PROVIDER_NOT_CONFIGURED);
        notification.setStatusMessage(message);
        notification.setNextAttemptAt(null);
    }

    @Transactional
    public void markFailed(Long notificationId, int maximumRetries, Instant nextRetryAt) {
        Notification notification = findNotification(notificationId);
        notification.setNextAttemptAt(null);
        if (notification.getRetryCount() >= maximumRetries) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setStatusMessage("Delivery failed after reaching the maximum of "
                    + maximumRetries + " retries");
            return;
        }

        notification.incrementRetryCount();
        notification.setStatus(NotificationStatus.RETRYING);
        notification.setStatusMessage("Delivery failed; retry scheduled");
        notification.setNextAttemptAt(nextRetryAt);
    }

    @Transactional
    public void markPermanentFailure(Long notificationId, String message) {
        Notification notification = findNotification(notificationId);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setStatusMessage(message);
        notification.setNextAttemptAt(null);
    }

    private Notification findNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }
}
