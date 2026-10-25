package com.example.demo.services;

import com.example.demo.models.Notification;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationRetrySystem {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRetrySystem.class);

    private final NotificationStatusUpdater statusUpdater;
    private final NotificationAdaptor notificationAdaptor;
    private final int maximumRetries;
    private final long initialDelaySeconds;

    public NotificationRetrySystem(
            NotificationStatusUpdater statusUpdater,
            NotificationAdaptor notificationAdaptor,
            @Value("${app.notifications.max-retries:5}") int maximumRetries,
            @Value("${app.notifications.retry-delay-seconds:10}") long initialDelaySeconds) {
        if (maximumRetries < 0 || initialDelaySeconds < 1) {
            throw new IllegalArgumentException("Retry configuration must be non-negative with a positive delay");
        }
        this.statusUpdater = statusUpdater;
        this.notificationAdaptor = notificationAdaptor;
        this.maximumRetries = maximumRetries;
        this.initialDelaySeconds = initialDelaySeconds;
    }

    public void processDueNotification(Long notificationId) {
        Notification notification = statusUpdater.claimForProcessing(notificationId, Instant.now(), maximumRetries);
        if (notification == null) {
            return;
        }

        try {
            notificationAdaptor.send(notification);
        } catch (ProviderNotConfiguredException exception) {
            statusUpdater.markProviderNotConfigured(notificationId, exception.getMessage());
            return;
        } catch (NotificationPermanentException exception) {
            statusUpdater.markPermanentFailure(notificationId, exception.getMessage());
            return;
        } catch (NotificationDeliveryException exception) {
            scheduleRetry(notificationId, notification.getRetryCount());
            return;
        } catch (RuntimeException exception) {
            logger.error("Unexpected notification provider failure id={}", notificationId, exception);
            statusUpdater.markPermanentFailure(notificationId, "Unexpected notification provider failure");
            return;
        }
        statusUpdater.markSent(notificationId);
    }

    private void scheduleRetry(Long notificationId, int retriesAlreadyScheduled) {
        long exponent = Math.min(retriesAlreadyScheduled, 20);
        long delaySeconds = initialDelaySeconds * (1L << exponent);
        statusUpdater.markFailed(
                notificationId,
                maximumRetries,
                Instant.now().plusSeconds(delaySeconds));
    }

    public int maximumRetries() {
        return maximumRetries;
    }
}
