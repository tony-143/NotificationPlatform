package com.example.demo.repositeries;

import com.example.demo.models.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByApiKey_IdAndIdempotencyHash(Long apiKeyId, String idempotencyHash);

    long deleteByExpiresAtBefore(Instant expirationTime);
}
