package com.example.demo.services;

import com.example.demo.models.NotificationStatus;
import com.example.demo.repositeries.NotificationRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final List<NotificationStatus> DUE_STATUSES =
            List.of(NotificationStatus.PENDING, NotificationStatus.RETRYING);

    private final NotificationRepository notificationRepository;
    private final NotificationRetrySystem retrySystem;
    private final int batchSize;

    public NotificationScheduler(
            NotificationRepository notificationRepository,
            NotificationRetrySystem retrySystem,
            @Value("${app.notifications.batch-size:100}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Notification scheduler batch size must be positive");
        }
        this.notificationRepository = notificationRepository;
        this.retrySystem = retrySystem;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval-ms:5000}")
    public void processDueNotifications() {
        Instant now = Instant.now();
        List<Long> dueNotificationIds = notificationRepository.findDueNotificationIds(
                DUE_STATUSES,
                NotificationStatus.PENDING,
                retrySystem.maximumRetries(),
                now,
                PageRequest.of(0, batchSize));
        for (Long notificationId : dueNotificationIds) {
            try {
                retrySystem.processDueNotification(notificationId);
            } catch (RuntimeException exception) {
                logger.error("Could not process due notification id={}", notificationId, exception);
            }
        }
    }
}
