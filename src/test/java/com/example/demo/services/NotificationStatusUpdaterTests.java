package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.models.User;
import com.example.demo.repositeries.NotificationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationStatusUpdaterTests {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationStatusUpdater updater;
    private Notification notification;

    @BeforeEach
    void setUp() {
        updater = new NotificationStatusUpdater(notificationRepository);
        notification = new Notification(
                org.mockito.Mockito.mock(User.class),
                "EMAIL",
                "person@example.com",
                "Hello",
                null);
        when(notificationRepository.findById(3L)).thenReturn(Optional.of(notification));
    }

    @Test
    void recordsFiveRetriesThenMarksExhaustedNotificationFailed() {
        for (int retry = 1; retry <= 5; retry++) {
            updater.markFailed(3L, 5, Instant.now().plusSeconds(10));
            assertEquals(retry, notification.getRetryCount());
            if (retry < 5) {
                assertEquals(NotificationStatus.RETRYING, notification.getStatus());
                assertNotNull(notification.getNextAttemptAt());
            }
        }

        updater.markFailed(3L, 5, Instant.now().plusSeconds(10));

        assertEquals(5, notification.getRetryCount());
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("Delivery failed after reaching the maximum of 5 retries", notification.getStatusMessage());
        org.junit.jupiter.api.Assertions.assertNull(notification.getNextAttemptAt());
        org.junit.jupiter.api.Assertions.assertNull(notification.getClosedAt());
    }

    @Test
    void recordsPermanentFailureWithoutSchedulingRetry() {
        updater.markPermanentFailure(3L, "Invalid recipient");

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("Invalid recipient", notification.getStatusMessage());
        org.junit.jupiter.api.Assertions.assertNull(notification.getNextAttemptAt());
    }
}
