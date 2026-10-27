package com.example.demo.services;

import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.models.User;
import com.example.demo.repositeries.NotificationRepository;
import com.example.demo.repositeries.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {

    private static final List<NotificationStatus> FAILURE_STATUSES =
            List.of(NotificationStatus.FAILED, NotificationStatus.PROVIDER_NOT_CONFIGURED);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public DashboardService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SummaryView summary(String email, String range) {
        TimeRange window = TimeRange.parse(range);
        Long tenantId = tenantId(email);

        Map<NotificationStatus, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countByStatus(tenantId, window.from())) {
            byStatus.put((NotificationStatus) row[0], ((Number) row[1]).longValue());
        }

        List<ChannelCount> channels = notificationRepository.countByType(tenantId, window.from())
                .stream()
                .map(row -> new ChannelCount(((String) row[0]).toLowerCase(Locale.ROOT), ((Number) row[1]).longValue()))
                .toList();

        long pending = count(byStatus, NotificationStatus.PENDING);
        long processing = count(byStatus, NotificationStatus.PROCESSING);
        long retrying = count(byStatus, NotificationStatus.RETRYING);
        long accepted = count(byStatus, NotificationStatus.SENT);
        long failed = count(byStatus, NotificationStatus.FAILED);
        long closed = count(byStatus, NotificationStatus.CLOSED);
        long notConfigured = count(byStatus, NotificationStatus.PROVIDER_NOT_CONFIGURED);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        long terminal = accepted + failed + closed + notConfigured;
        Double successRate = terminal == 0
                ? null
                : Math.round((accepted * 10000.0) / terminal) / 100.0;

        List<StatusCount> statuses = byStatus.entrySet().stream()
                .map(entry -> new StatusCount(entry.getKey().name(), entry.getValue()))
                .sorted(Comparator.comparingLong(StatusCount::count).reversed())
                .toList();

        return new SummaryView(
                window.key(),
                window.from().toString(),
                Instant.now().toString(),
                total,
                pending,
                processing,
                retrying,
                accepted,
                failed,
                closed,
                notConfigured,
                successRate,
                false,
                channels,
                statuses);
    }

    @Transactional(readOnly = true)
    public TimeSeriesView series(String email, String range) {
        TimeRange window = TimeRange.parse(range);
        Long tenantId = tenantId(email);

        Map<Instant, Map<String, Long>> buckets = new LinkedHashMap<>();
        Map<Instant, Long> failures = new LinkedHashMap<>();

        List<Object[]> aggregateRows = "hour".equals(window.bucket())
                ? notificationRepository.aggregateByHour(tenantId, window.from())
                : notificationRepository.aggregateByDay(tenantId, window.from());

        for (Object[] row : aggregateRows) {
            Instant bucket = toInstant(row[0]);
            String channel = ((String) row[1]).toLowerCase(Locale.ROOT);
            String status = (String) row[2];
            long count = ((Number) row[3]).longValue();

            buckets.computeIfAbsent(bucket, key -> new LinkedHashMap<>()).merge(channel, count, Long::sum);
            if (FAILURE_STATUSES.stream().anyMatch(failure -> failure.name().equals(status))) {
                failures.merge(bucket, count, Long::sum);
            }
        }

        List<SeriesPoint> points = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    long pointTotal = entry.getValue().values().stream().mapToLong(Long::longValue).sum();
                    return new SeriesPoint(
                            entry.getKey().toString(),
                            pointTotal,
                            entry.getValue(),
                            failures.getOrDefault(entry.getKey(), 0L));
                })
                .toList();

        return new TimeSeriesView(
                window.key(),
                window.bucket(),
                window.from().toString(),
                Instant.now().toString(),
                points);
    }

    @Transactional(readOnly = true)
    public PageView history(
            String email,
            int page,
            int size,
            String status,
            String channel,
            Instant from,
            Instant to,
            String search) {
        Long tenantId = tenantId(email);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        NotificationStatus statusFilter = parseStatus(status);
        String typeFilter = channel == null || channel.isBlank() ? null : channel.trim().toUpperCase(Locale.ROOT);
        String recipientFilter = search == null || search.isBlank()
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        Specification<Notification> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenant").get("id"), tenantId));
            if (statusFilter != null) {
                predicates.add(builder.equal(root.get("status"), statusFilter));
            }
            if (typeFilter != null) {
                predicates.add(builder.equal(root.get("notificationType"), typeFilter));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (recipientFilter != null) {
                predicates.add(builder.like(builder.lower(root.get("recipient")), recipientFilter));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Notification> result = notificationRepository.findAll(
                specification,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<NotificationRow> items = result.getContent().stream()
                .map(notification -> new NotificationRow(
                        notification.getId(),
                        notification.getNotificationType().toLowerCase(Locale.ROOT),
                        maskRecipient(notification.getRecipient()),
                        notification.getStatus().name(),
                        notification.getCreatedAt().toString(),
                        asString(notification.getScheduledAt()),
                        notification.getUpdatedAt().toString(),
                        notification.getRetryCount()))
                .toList();

        return new PageView(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NotificationDetail detail(String email, Long notificationId) {
        Long tenantId = tenantId(email);
        Notification notification = notificationRepository.findByIdAndTenant_Id(notificationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        List<TimelineEvent> timeline = new ArrayList<>();
        timeline.add(new TimelineEvent(
                notification.getCreatedAt().toString(),
                "Created",
                "Notification accepted with status " + NotificationStatus.PENDING.name()));

        if (notification.getScheduledAt() != null) {
            timeline.add(new TimelineEvent(
                    notification.getScheduledAt().toString(),
                    "Scheduled",
                    "Delivery scheduled for this time"));
        }
        if (notification.getRetryCount() > 0) {
            timeline.add(new TimelineEvent(
                    notification.getUpdatedAt().toString(),
                    "Retried " + notification.getRetryCount() + "x",
                    "The provider attempt was retried"));
        }
        if (notification.getNextAttemptAt() != null
                && notification.getStatus() == NotificationStatus.RETRYING) {
            timeline.add(new TimelineEvent(
                    notification.getNextAttemptAt().toString(),
                    "Next attempt",
                    "Retry queued"));
        }
        timeline.add(new TimelineEvent(
                notification.getUpdatedAt().toString(),
                notification.getStatus().name(),
                notification.getStatusMessage() == null ? "Current status" : notification.getStatusMessage()));
        if (notification.getClosedAt() != null) {
            timeline.add(new TimelineEvent(
                    notification.getClosedAt().toString(),
                    "Closed",
                    "Notification closed"));
        }
        timeline.sort(Comparator.comparing(TimelineEvent::at));

        return new NotificationDetail(
                notification.getId(),
                notification.getNotificationType().toLowerCase(Locale.ROOT),
                maskRecipient(notification.getRecipient()),
                notification.getStatus().name(),
                notification.getStatusMessage(),
                notification.getMessage(),
                notification.getPayload(),
                notification.getCreatedAt().toString(),
                notification.getUpdatedAt().toString(),
                asString(notification.getScheduledAt()),
                asString(notification.getNextAttemptAt()),
                asString(notification.getClosedAt()),
                asString(notification.getExpiresAt()),
                notification.getRetryCount(),
                timeline);
    }

    private Long tenantId(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return user.getId();
    }

    private static long count(Map<NotificationStatus, Long> counts, NotificationStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    private static NotificationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return NotificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown notification status filter");
        }
    }

    private static String asString(Instant value) {
        return Optional.ofNullable(value).map(Instant::toString).orElse(null);
    }

    private static String maskRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "";
        }
        int at = recipient.indexOf('@');
        if (at > 0) {
            String local = recipient.substring(0, at);
            String visible = local.substring(0, Math.min(2, local.length()));
            return visible + "***" + recipient.substring(at);
        }
        String digits = recipient.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return "***" + digits.substring(digits.length() - 4);
        }
        return recipient.substring(0, 1) + "***";
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            throw new IllegalStateException("Bucket timestamp was null");
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        throw new IllegalStateException("Unsupported bucket timestamp type: " + value.getClass().getName());
    }

    public record TimeRange(String key, Instant from, String bucket) {

        static TimeRange parse(String raw) {
            String key = raw == null ? "7d" : raw.trim().toLowerCase(Locale.ROOT);
            Instant now = Instant.now();
            return switch (key) {
                case "24h" -> new TimeRange("24h", now.minus(Duration.ofHours(24)), "hour");
                case "30d" -> new TimeRange("30d", now.minus(Duration.ofDays(30)), "day");
                case "7d" -> new TimeRange("7d", now.minus(Duration.ofDays(7)), "day");
                default -> throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Range must be one of 24h, 7d, 30d");
            };
        }
    }

    public record SummaryView(
            String range,
            String from,
            String generatedAt,
            long total,
            long pending,
            long processing,
            long retrying,
            long accepted,
            long failed,
            long closed,
            long providerNotConfigured,
            Double successRate,
            boolean deliveryConfirmationSupported,
            List<ChannelCount> channels,
            List<StatusCount> statuses) {
    }

    public record ChannelCount(String channel, long count) {
    }

    public record StatusCount(String status, long count) {
    }

    public record TimeSeriesView(
            String range,
            String bucket,
            String from,
            String to,
            List<SeriesPoint> points) {
    }

    public record SeriesPoint(String start, long total, Map<String, Long> channels, long failed) {
    }

    public record PageView(
            List<NotificationRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record NotificationRow(
            Long id,
            String channel,
            String recipient,
            String status,
            String createdAt,
            String scheduledAt,
            String updatedAt,
            int retryCount) {
    }

    public record NotificationDetail(
            Long id,
            String channel,
            String recipient,
            String status,
            String statusMessage,
            String message,
            String payload,
            String createdAt,
            String updatedAt,
            String scheduledAt,
            String nextAttemptAt,
            String closedAt,
            String expiresAt,
            int retryCount,
            List<TimelineEvent> timeline) {
    }

    public record TimelineEvent(String at, String label, String detail) {
    }
}
