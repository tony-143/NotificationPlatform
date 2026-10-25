package com.example.demo.repositeries;

import com.example.demo.models.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHashAndUsedFalse(String tokenHash);

    @Modifying
    @Query("update EmailVerificationToken t set t.used = true where t.user.id = :userId and t.used = false")
    int invalidateAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from EmailVerificationToken t where t.expiresAt < :now or t.used = true")
    int deleteExpiredOrUsed(@Param("now") Instant now);
}
