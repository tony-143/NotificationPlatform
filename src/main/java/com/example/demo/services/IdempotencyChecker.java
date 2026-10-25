package com.example.demo.services;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Notification;
import com.example.demo.repositeries.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdempotencyChecker {

    public static final Duration WINDOW = Duration.ofMinutes(5);

    private final IdempotencyRecordRepository repository;

    public IdempotencyChecker(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    public Optional<IdempotencyRecord> findExisting(
            ApiKeys apiKey,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        String idempotencyHash = Sha256Hasher.hash(idempotencyKey);
        Optional<IdempotencyRecord> record = repository
                .findByApiKey_IdAndIdempotencyHash(apiKey.getId(), idempotencyHash);
        if (record.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord existing = record.get();
        if (!existing.getExpiresAt().isAfter(now)) {
            repository.delete(existing);
            repository.flush();
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(
                existing.getRequestHash().getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request");
        }
        return Optional.of(existing);
    }

    public void save(
            ApiKeys apiKey,
            String idempotencyKey,
            String requestHash,
            Notification notification,
            Instant now) {
        repository.save(new IdempotencyRecord(
                apiKey,
                Sha256Hasher.hash(idempotencyKey),
                requestHash,
                notification,
                now.plus(WINDOW)));
    }

    @Transactional
    public long removeExpired(Instant now) {
        return repository.deleteByExpiresAtBefore(now);
    }

}
