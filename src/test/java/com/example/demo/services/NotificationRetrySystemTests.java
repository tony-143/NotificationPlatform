package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.Notification;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySystemTests {

    @Mock
    private NotificationStatusUpdater statusUpdater;

    @Mock
    private NotificationAdaptor notificationAdaptor;

    private NotificationRetrySystem retrySystem;

    @BeforeEach
    void setUp() {
        retrySystem = new NotificationRetrySystem(statusUpdater, notificationAdaptor, 5, 10);
    }

    @Test
    void successfulDeliveryMarksNotificationSent() {
        Notification notification = org.mockito.Mockito.mock(Notification.class);
        when(statusUpdater.claimForProcessing(eq(7L), any(), eq(5))).thenReturn(notification);

        retrySystem.processDueNotification(7L);

        verify(notificationAdaptor).send(notification);
        verify(statusUpdater).markSent(7L);
    }

    @Test
    void deliveryFailureSchedulesRetryAndIncrementsRetrySequence() {
        Notification notification = org.mockito.Mockito.mock(Notification.class);
        when(notification.getRetryCount()).thenReturn(2);
        when(statusUpdater.claimForProcessing(eq(7L), any(), eq(5))).thenReturn(notification);
        doThrow(new NotificationDeliveryException("provider failed", new IllegalStateException()))
                .when(notificationAdaptor)
                .send(notification);

        Instant beforeProcessing = Instant.now();
        retrySystem.processDueNotification(7L);

        ArgumentCaptor<Instant> nextRetryAt = ArgumentCaptor.forClass(Instant.class);
        verify(statusUpdater).markFailed(eq(7L), eq(5), nextRetryAt.capture());
        assertTrue(nextRetryAt.getValue().isAfter(beforeProcessing.plusSeconds(39)));
        assertTrue(nextRetryAt.getValue().isBefore(Instant.now().plusSeconds(41)));
    }

    @Test
    void missingProviderIsNotReportedAsSuccessfulOrRetried() {
        Notification notification = org.mockito.Mockito.mock(Notification.class);
        when(statusUpdater.claimForProcessing(eq(7L), any(), eq(5))).thenReturn(notification);
        doThrow(new ProviderNotConfiguredException("Email provider is not configured"))
                .when(notificationAdaptor)
                .send(notification);

        retrySystem.processDueNotification(7L);

        verify(statusUpdater).markProviderNotConfigured(7L, "Email provider is not configured");
        verify(statusUpdater, never()).markSent(7L);
        verify(statusUpdater, never()).markFailed(any(), anyInt(), any());
    }

    @Test
    void permanentProviderFailureIsNotRetried() {
        Notification notification = org.mockito.Mockito.mock(Notification.class);
        when(statusUpdater.claimForProcessing(eq(7L), any(), eq(5))).thenReturn(notification);
        doThrow(new NotificationPermanentException("Invalid recipient"))
                .when(notificationAdaptor)
                .send(notification);

        retrySystem.processDueNotification(7L);

        verify(statusUpdater).markPermanentFailure(7L, "Invalid recipient");
        verify(statusUpdater, never()).markFailed(any(), anyInt(), any());
    }

    @Test
    void notificationWithoutSuccessfulClaimIsNotSent() {
        when(statusUpdater.claimForProcessing(eq(7L), any(), eq(5))).thenReturn(null);

        retrySystem.processDueNotification(7L);

        verify(notificationAdaptor, never()).send(any());
        verify(statusUpdater, never()).markSent(7L);
    }
}
