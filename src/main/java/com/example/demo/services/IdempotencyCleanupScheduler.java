package com.example.demo.services;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCleanupScheduler {

    private final IdempotencyChecker idempotencyChecker;

    public IdempotencyCleanupScheduler(IdempotencyChecker idempotencyChecker) {
        this.idempotencyChecker = idempotencyChecker;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval-ms:60000}")
    public void removeExpiredRecords() {
        idempotencyChecker.removeExpired(Instant.now());
    }
}
