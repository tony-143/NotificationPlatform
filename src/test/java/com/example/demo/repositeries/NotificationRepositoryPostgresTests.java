package com.example.demo.repositeries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import com.example.demo.models.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_TEST_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationRepositoryPostgresTests {

    private static final List<NotificationStatus> DUE_STATUSES =
            List.of(NotificationStatus.PENDING, NotificationStatus.RETRYING);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;
    private Long tenantId;
    private Long otherTenantId;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("NOTIFICATION_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("NOTIFICATION_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("NOTIFICATION_TEST_DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @BeforeEach
    void createIsolatedTenant() {
        transactions = new TransactionTemplate(transactionManager);
        tenantId = transactions.execute(status -> userRepository.saveAndFlush(
                new User("Notification test", UUID.randomUUID() + "@example.test", "not-a-real-password")).getId());
        otherTenantId = transactions.execute(status -> userRepository.saveAndFlush(
                new User("Other tenant", UUID.randomUUID() + "@example.test", "not-a-real-password")).getId());
    }

    @AfterEach
    void removeTestData() {
        transactions.executeWithoutResult(status -> {
            for (Long id : List.of(tenantId, otherTenantId)) {
                userRepository.findById(id).ifPresent(user -> {
                    notificationRepository.deleteAll(
                            notificationRepository.findByTenant_IdOrderByCreatedAtDesc(id));
                    userRepository.delete(user);
                });
            }
        });
    }

    @Test
    void dueQueryExcludesFutureSchedulesAndRetryTimes() {
        Instant now = Instant.now();
        Long immediate = createNotification(null, null, null);
        Long scheduledDue = createNotification(now.minusSeconds(5), null, null);
        Long scheduledFuture = createNotification(now.plusSeconds(3600), null, null);
        Long retryDue = createNotification(null, NotificationStatus.RETRYING, now.minusSeconds(5));
        Long retryFuture = createNotification(null, NotificationStatus.RETRYING, now.plusSeconds(3600));
        Long retriesExhausted = createNotification(null, NotificationStatus.RETRYING, now.minusSeconds(5), 5);

        List<Long> dueIds = notificationRepository.findDueNotificationIds(
                DUE_STATUSES,
                NotificationStatus.PENDING,
                5,
                now,
                PageRequest.of(0, 100));

        assertTrue(dueIds.contains(immediate));
        assertTrue(dueIds.contains(scheduledDue));
        assertTrue(dueIds.contains(retryDue));
        assertFalse(dueIds.contains(scheduledFuture));
        assertFalse(dueIds.contains(retryFuture));
        assertFalse(dueIds.contains(retriesExhausted));
    }

    @Test
    void concurrentWorkersCanClaimOnlyOnce() throws Exception {
        Long notificationId = createNotification(null, null, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = workers.submit(() -> claim(notificationId, ready, start));
            Future<Integer> second = workers.submit(() -> claim(notificationId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }

        Notification claimed = notificationRepository.findById(notificationId).orElseThrow();
        assertEquals(NotificationStatus.PROCESSING, claimed.getStatus());
    }

    @Test
    void dashboardAggregatesAreScopedToTheRequestedTenant() {
        Instant from = Instant.now().minusSeconds(3600);
        createForTenant(tenantId, "EMAIL", NotificationStatus.SENT, "a@example.test");
        createForTenant(tenantId, "EMAIL", NotificationStatus.FAILED, "b@example.test");
        createForTenant(tenantId, "SMS", NotificationStatus.PENDING, "+919876543210");
        createForTenant(otherTenantId, "EMAIL", NotificationStatus.SENT, "other@example.test");

        assertEquals(3, sumColumn(notificationRepository.countByStatus(tenantId, from), 1));
        assertEquals(3, sumColumn(notificationRepository.countByType(tenantId, from), 1));

        List<Object[]> byDay = notificationRepository.aggregateByDay(tenantId, from.minusSeconds(86400));
        assertFalse(byDay.isEmpty());
        assertEquals(3, sumColumn(byDay, 3));

        List<Object[]> byHour = notificationRepository.aggregateByHour(tenantId, from.minusSeconds(86400));
        assertFalse(byHour.isEmpty());
        assertEquals(3, sumColumn(byHour, 3));
    }

    @Test
    void specificationSearchFiltersByTenantAndStatus() {
        createForTenant(tenantId, "EMAIL", NotificationStatus.SENT, "sent@example.test");
        createForTenant(tenantId, "EMAIL", NotificationStatus.PENDING, "pending@example.test");
        createForTenant(otherTenantId, "EMAIL", NotificationStatus.SENT, "other@example.test");

        Specification<Notification> specification = (root, query, builder) -> builder.and(
                builder.equal(root.get("tenant").get("id"), tenantId),
                builder.equal(root.get("status"), NotificationStatus.SENT));

        Page<Notification> page = notificationRepository.findAll(
                specification,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(1, page.getTotalElements());
        assertEquals("sent@example.test", page.getContent().get(0).getRecipient());
    }

    private Long createForTenant(
            Long tenant,
            String type,
            NotificationStatus status,
            String recipient) {
        return transactions.execute(transactionStatus -> {
            User owner = userRepository.findById(tenant).orElseThrow();
            Notification notification = new Notification(
                    owner, type, recipient, "Repository dashboard test", null);
            if (status != null) {
                notification.setStatus(status);
            }
            return notificationRepository.saveAndFlush(notification).getId();
        });
    }

    private long sumColumn(List<Object[]> rows, int column) {
        return rows.stream().mapToLong(row -> ((Number) row[column]).longValue()).sum();
    }

    private Long createNotification(
            Instant scheduledAt,
            NotificationStatus status,
            Instant nextAttemptAt) {
        return createNotification(scheduledAt, status, nextAttemptAt, 0);
    }

    private Long createNotification(
            Instant scheduledAt,
            NotificationStatus status,
            Instant nextAttemptAt,
            int retryCount) {
        return transactions.execute(transactionStatus -> {
            User tenant = userRepository.findById(tenantId).orElseThrow();
            Notification notification = new Notification(
                    tenant,
                    "EMAIL",
                    "person@example.test",
                    "Repository test",
                    null,
                    scheduledAt);
            if (status != null) {
                notification.setStatus(status);
                notification.setNextAttemptAt(nextAttemptAt);
                for (int attempt = 0; attempt < retryCount; attempt++) {
                    notification.incrementRetryCount();
                }
            }
            return notificationRepository.saveAndFlush(notification).getId();
        });
    }

    private int claim(Long notificationId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return transactions.execute(transactionStatus -> notificationRepository.claimForProcessing(
                notificationId,
                DUE_STATUSES,
                NotificationStatus.PENDING,
                5,
                NotificationStatus.PROCESSING,
                "Claimed by repository integration test",
                Instant.now()));
    }
}
