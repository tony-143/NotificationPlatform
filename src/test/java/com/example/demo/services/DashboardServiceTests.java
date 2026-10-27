package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.models.User;
import com.example.demo.repositeries.NotificationRepository;
import com.example.demo.repositeries.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

    private static final String EMAIL = "owner@example.com";
    private static final long TENANT_ID = 7L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(notificationRepository, userRepository);
        User user = mock(User.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(TENANT_ID);
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));
    }

    @Test
    void summaryUsesTenantScopedCountsAndComputesSuccessRate() {
        when(notificationRepository.countByStatus(eq(TENANT_ID), any(Instant.class))).thenReturn(List.of(
                new Object[] {NotificationStatus.SENT, 3L},
                new Object[] {NotificationStatus.FAILED, 1L},
                new Object[] {NotificationStatus.PENDING, 2L}));
        when(notificationRepository.countByType(eq(TENANT_ID), any(Instant.class))).thenReturn(List.of(
                new Object[] {"EMAIL", 5L},
                new Object[] {"SMS", 1L}));

        DashboardService.SummaryView summary = service.summary(EMAIL, "7d");

        assertEquals("7d", summary.range());
        assertEquals(6, summary.total());
        assertEquals(3, summary.accepted());
        assertEquals(1, summary.failed());
        assertEquals(2, summary.pending());
        assertEquals(75.0, summary.successRate());
        assertEquals(false, summary.deliveryConfirmationSupported());
        assertEquals("email", summary.channels().get(0).channel());
        assertEquals(5, summary.channels().get(0).count());
        assertEquals(3, summary.statuses().size());
    }

    @Test
    void successRateIsNullWhenNothingHasReachedATerminalState() {
        when(notificationRepository.countByStatus(eq(TENANT_ID), any(Instant.class)))
                .thenReturn(List.<Object[]>of(new Object[] {NotificationStatus.PENDING, 4L}));
        when(notificationRepository.countByType(eq(TENANT_ID), any(Instant.class))).thenReturn(List.<Object[]>of());

        DashboardService.SummaryView summary = service.summary(EMAIL, "24h");

        assertEquals(4, summary.total());
        assertNull(summary.successRate());
    }

    @Test
    void rejectsUnsupportedRanges() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.summary(EMAIL, "90d"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void timeseriesBucketsChannelsAndFailuresFromNativeRows() {
        OffsetDateTime bucket = OffsetDateTime.parse("2026-10-27T00:00:00Z");
        when(notificationRepository.aggregateByDay(eq(TENANT_ID), any(Instant.class))).thenReturn(List.of(
                new Object[] {bucket, "EMAIL", "SENT", 2L},
                new Object[] {bucket, "SMS", "FAILED", 1L},
                new Object[] {bucket, "EMAIL", "PROVIDER_NOT_CONFIGURED", 1L}));

        DashboardService.TimeSeriesView series = service.series(EMAIL, "30d");

        assertEquals("30d", series.range());
        assertEquals("day", series.bucket());
        assertEquals(1, series.points().size());
        DashboardService.SeriesPoint point = series.points().get(0);
        assertEquals(4, point.total());
        assertEquals(3, point.channels().get("email"));
        assertEquals(1, point.channels().get("sms"));
        assertEquals(2, point.failed());
    }

    @Test
    void timeseriesUsesHourlyAggregateFor24h() {
        when(notificationRepository.aggregateByHour(eq(TENANT_ID), any(Instant.class)))
                .thenReturn(List.<Object[]>of());

        DashboardService.TimeSeriesView series = service.series(EMAIL, "24h");

        assertEquals("hour", series.bucket());
        assertTrue(series.points().isEmpty());
        verify(notificationRepository).aggregateByHour(eq(TENANT_ID), any(Instant.class));
    }

    @Test
    void historyMasksRecipientsAndClampsPageSize() {
        Notification notification = new Notification(
                mock(User.class), "EMAIL", "alice@example.com", "Hello", null);
        when(notificationRepository.findAll(
                        any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification), Pageable.ofSize(10), 1));

        DashboardService.PageView page = service.history(EMAIL, -3, 500, null, null, null, null, null);

        assertEquals(0, page.page());
        assertEquals("al***@example.com", page.items().get(0).recipient());
        assertEquals("email", page.items().get(0).channel());
        assertEquals("PENDING", page.items().get(0).status());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void historyMasksPhoneNumbersKeepingTheLastFourDigits() {
        Notification notification = new Notification(
                mock(User.class), "SMS", "+919876543210", "Code", null);
        when(notificationRepository.findAll(
                        any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification), Pageable.ofSize(10), 1));

        DashboardService.PageView page = service.history(EMAIL, 0, 10, null, null, null, null, null);

        assertEquals("***3210", page.items().get(0).recipient());
    }

    @Test
    void detailIsScopedToTheSignedInTenant() {
        when(notificationRepository.findByIdAndTenant_Id(99L, TENANT_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.detail(EMAIL, 99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(notificationRepository).findByIdAndTenant_Id(99L, TENANT_ID);
    }

    @Test
    void detailReturnsMaskedRecipientAndTimelineForOwnedNotification() {
        Notification notification = new Notification(
                mock(User.class), "EMAIL", "bob@example.com", "Message body", "{\"a\":1}");
        when(notificationRepository.findByIdAndTenant_Id(1L, TENANT_ID))
                .thenReturn(Optional.of(notification));

        DashboardService.NotificationDetail detail = service.detail(EMAIL, 1L);

        assertEquals("bo***@example.com", detail.recipient());
        assertEquals("Message body", detail.message());
        assertEquals("{\"a\":1}", detail.payload());
        assertTrue(detail.timeline().size() >= 2);
    }

    @Test
    void historyRejectsUnknownStatusFilter() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.history(EMAIL, 0, 10, "NOT_A_STATUS", null, null, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
