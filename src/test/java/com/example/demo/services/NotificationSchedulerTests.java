package com.example.demo.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.NotificationStatus;
import com.example.demo.repositeries.NotificationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRetrySystem retrySystem;

    @Test
    void fetchesDuePendingAndRetryingNotificationsAndProcessesEach() {
        when(retrySystem.maximumRetries()).thenReturn(5);
        when(notificationRepository.findDueNotificationIds(
                any(),
                any(),
                anyInt(),
                any(),
                any(Pageable.class))).thenReturn(List.of(4L, 8L));
        NotificationScheduler scheduler = new NotificationScheduler(
                notificationRepository,
                retrySystem,
                100);

        scheduler.processDueNotifications();

        verify(notificationRepository).findDueNotificationIds(
                eq(List.of(NotificationStatus.PENDING, NotificationStatus.RETRYING)),
                eq(NotificationStatus.PENDING),
                eq(5),
                any(),
                eq(PageRequest.of(0, 100)));
        verify(retrySystem).processDueNotification(4L);
        verify(retrySystem).processDueNotification(8L);
    }
}
