package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Notification;
import com.example.demo.repositeries.IdempotencyRecordRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class IdempotencyCheckerTests {

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyChecker checker;

    @BeforeEach
    void setUp() {
        checker = new IdempotencyChecker(repository);
    }

    @Test
    void returnsExistingRecordWithinFiveMinuteWindow() {
        Instant now = Instant.now();
        ApiKeys apiKey = org.mockito.Mockito.mock(ApiKeys.class);
        when(apiKey.getId()).thenReturn(17L);
        IdempotencyRecord record = org.mockito.Mockito.mock(IdempotencyRecord.class);
        when(record.getExpiresAt()).thenReturn(now.plusSeconds(30));
        when(record.getRequestHash()).thenReturn("same-request-hash");
        when(repository.findByApiKey_IdAndIdempotencyHash(
                17L,
                Sha256Hasher.hash("same-key"))).thenReturn(Optional.of(record));

        Optional<IdempotencyRecord> result = checker.findExisting(
                apiKey,
                "same-key",
                "same-request-hash",
                now);

        assertTrue(result.isPresent());
        assertEquals(record, result.get());
    }

    @Test
    void rejectsReusingIdempotencyKeyWithDifferentRequest() {
        Instant now = Instant.now();
        ApiKeys apiKey = org.mockito.Mockito.mock(ApiKeys.class);
        when(apiKey.getId()).thenReturn(17L);
        IdempotencyRecord record = org.mockito.Mockito.mock(IdempotencyRecord.class);
        when(record.getExpiresAt()).thenReturn(now.plusSeconds(30));
        when(record.getRequestHash()).thenReturn("original-request-hash");
        when(repository.findByApiKey_IdAndIdempotencyHash(
                17L,
                Sha256Hasher.hash("same-key"))).thenReturn(Optional.of(record));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> checker.findExisting(apiKey, "same-key", "different-request-hash", now));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void removesExpiredKeyBeforeAllowingNewRequest() {
        Instant now = Instant.now();
        ApiKeys apiKey = org.mockito.Mockito.mock(ApiKeys.class);
        when(apiKey.getId()).thenReturn(17L);
        IdempotencyRecord record = org.mockito.Mockito.mock(IdempotencyRecord.class);
        when(record.getExpiresAt()).thenReturn(now);
        when(repository.findByApiKey_IdAndIdempotencyHash(
                17L,
                Sha256Hasher.hash("same-key"))).thenReturn(Optional.of(record));

        Optional<IdempotencyRecord> result = checker.findExisting(apiKey, "same-key", "request", now);

        assertFalse(result.isPresent());
        verify(repository).delete(record);
        verify(repository).flush();
    }

    @Test
    void storesOnlyHashedIdempotencyKeyAndSetsFiveMinuteExpiry() {
        Instant now = Instant.now();
        ApiKeys apiKey = org.mockito.Mockito.mock(ApiKeys.class);
        Notification notification = org.mockito.Mockito.mock(Notification.class);
        checker.save(apiKey, "plain-idempotency-key", "request-hash", notification, now);

        org.mockito.ArgumentCaptor<IdempotencyRecord> record =
                org.mockito.ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(record.capture());
        assertEquals(Sha256Hasher.hash("plain-idempotency-key"), record.getValue().getIdempotencyHash());
        assertEquals(now.plus(IdempotencyChecker.WINDOW), record.getValue().getExpiresAt());
    }
}
