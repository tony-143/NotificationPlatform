package com.example.demo.repositeries;

import com.example.demo.models.Notification;
import com.example.demo.models.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTenant_IdOrderByCreatedAtDesc(Long tenantId);

    Optional<Notification> findByIdAndTenant_Id(Long id, Long tenantId);

    @Query("""
            select n.id from Notification n
            where n.status in :statuses
              and (n.status = :pendingStatus or n.retryCount < :maximumRetries)
              and (n.scheduledAt is null or n.scheduledAt <= :now)
              and (n.nextAttemptAt is null or n.nextAttemptAt <= :now)
              and (n.expiresAt is null or n.expiresAt > :now)
            order by n.scheduledAt, n.nextAttemptAt, n.createdAt, n.id
            """)
    List<Long> findDueNotificationIds(
            @Param("statuses") List<NotificationStatus> statuses,
            @Param("pendingStatus") NotificationStatus pendingStatus,
            @Param("maximumRetries") int maximumRetries,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying
    @Query("""
            update Notification n
            set n.status = :processing,
                n.statusMessage = :message,
                n.updatedAt = :now,
                n.version = n.version + 1
            where n.id = :id
              and n.status in :dueStatuses
              and (n.status = :pendingStatus or n.retryCount < :maximumRetries)
              and (n.scheduledAt is null or n.scheduledAt <= :now)
              and (n.nextAttemptAt is null or n.nextAttemptAt <= :now)
              and (n.expiresAt is null or n.expiresAt > :now)
            """)
    int claimForProcessing(
            @Param("id") Long id,
            @Param("dueStatuses") List<NotificationStatus> dueStatuses,
            @Param("pendingStatus") NotificationStatus pendingStatus,
            @Param("maximumRetries") int maximumRetries,
            @Param("processing") NotificationStatus processing,
            @Param("message") String message,
            @Param("now") Instant now);
}
