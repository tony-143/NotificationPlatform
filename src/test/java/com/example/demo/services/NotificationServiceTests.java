package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.models.User;
import com.example.demo.repositeries.ApiKeyRepository;
import com.example.demo.repositeries.NotificationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdempotencyChecker idempotencyChecker;

    @Mock
    private NotificationRequestValidator requestValidator;

    @Mock
    private NotificationRequestHasher requestHasher;

    private NotificationService notificationService;
    private ApiKeys apiKey;
    private User owner;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                apiKeyRepository,
                notificationRepository,
                idempotencyChecker,
                requestValidator,
                requestHasher);
        apiKey = org.mockito.Mockito.mock(ApiKeys.class);
        owner = org.mockito.Mockito.mock(User.class);
        when(apiKey.getKeyStatus()).thenReturn(ApiKeys.ACTIVE);
    }

    @Test
    void newNotificationIsStoredPendingForSchedulerProcessing() {
        when(apiKey.getUser()).thenReturn(owner);
        when(apiKeyRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(apiKey));
        when(idempotencyChecker.findExisting(any(), any(), any(), any())).thenReturn(Optional.empty());
        NotificationService.NotificationRequest request = new NotificationService.NotificationRequest(
                "EMAIL",
                "person@example.com",
                "Hello",
                null);
        NotificationRequestValidator.ValidatedRequest validated = new NotificationRequestValidator.ValidatedRequest(
                "EMAIL",
                "person@example.com",
                "Hello",
                null,
                null);
        when(requestValidator.validate(request)).thenReturn(validated);
        when(requestHasher.hash(validated)).thenReturn("request-hash");
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationService.NotificationOutcome outcome = notificationService.submit(
                11L,
                "request-123",
                request);

        assertEquals(202, outcome.httpStatus());
        assertEquals(NotificationStatus.PENDING, outcome.notification().status());
        assertNull(outcome.notification().scheduledAt());
        verify(notificationRepository).saveAndFlush(any(Notification.class));
        verify(idempotencyChecker).save(any(), any(), any(), any(), any());
    }

    @Test
    void repeatedIdempotencyKeyReturnsStoredPendingNotification() {
        when(apiKeyRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(apiKey));
        Notification storedNotification = new Notification(
                owner,
                "EMAIL",
                "person@example.com",
                "Hello",
                null);
        IdempotencyRecord record = org.mockito.Mockito.mock(IdempotencyRecord.class);
        when(record.getNotification()).thenReturn(storedNotification);
        when(idempotencyChecker.findExisting(any(), any(), any(), any())).thenReturn(Optional.of(record));
        NotificationService.NotificationRequest request = new NotificationService.NotificationRequest(
                "EMAIL",
                "person@example.com",
                "Hello",
                null);
        NotificationRequestValidator.ValidatedRequest validated = new NotificationRequestValidator.ValidatedRequest(
                "EMAIL",
                "person@example.com",
                "Hello",
                null,
                null);
        when(requestValidator.validate(request)).thenReturn(validated);
        when(requestHasher.hash(validated)).thenReturn("request-hash");

        NotificationService.NotificationOutcome outcome = notificationService.submit(
                11L,
                "request-123",
                request);

        assertTrue(outcome.duplicate());
        assertEquals(200, outcome.httpStatus());
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void notificationStatusLookupIsScopedToApiKeyOwner() {
        when(apiKeyRepository.findById(11L)).thenReturn(Optional.of(apiKey));
        when(apiKey.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(34L);
        Notification notification = new Notification(owner, "EMAIL", "person@example.com", "Hello", null);
        when(notificationRepository.findByIdAndTenant_Id(7L, 34L)).thenReturn(Optional.of(notification));

        NotificationService.NotificationView view = notificationService.getNotification(11L, 7L);

        assertEquals(NotificationStatus.PENDING, view.status());
        verify(notificationRepository).findByIdAndTenant_Id(7L, 34L);
    }

    @Test
    void statusLookupReturnsNotFoundForNotificationsOutsideApiKeyOwner() {
        when(apiKeyRepository.findById(11L)).thenReturn(Optional.of(apiKey));
        when(apiKey.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(34L);
        when(notificationRepository.findByIdAndTenant_Id(7L, 34L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> notificationService.getNotification(11L, 7L));

        assertEquals(404, exception.getStatusCode().value());
    }
}
