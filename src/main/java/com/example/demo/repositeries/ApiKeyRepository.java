package com.example.demo.repositeries;

import com.example.demo.models.ApiKeys;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKeys, Long> {

    @Query("""
            select apiKey from ApiKeys apiKey
            where apiKey.keyHash = :keyHash
              and apiKey.keyStatus = :keyStatus
              and (apiKey.expiresAt is null or apiKey.expiresAt > :now)
            """)
    Optional<ApiKeys> findUsableKey(
            @Param("keyHash") String keyHash,
            @Param("keyStatus") String keyStatus,
            @Param("now") Instant now);

    Optional<ApiKeys> findByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select apiKey from ApiKeys apiKey where apiKey.id = :id")
    Optional<ApiKeys> findByIdForUpdate(@Param("id") Long id);
}
