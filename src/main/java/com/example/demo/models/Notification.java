package com.example.demo.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @Column(name = "notification_type", nullable = false, length = 32)
    private String notificationType;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationStatus status;

    @Column(length = 512)
    private String statusMessage;

    @Column(nullable = false, length = 512)
    private String recipient;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private int retryCount;

    private Instant nextAttemptAt;

    private Instant closedAt;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 16)
    private String priority;

    private Instant scheduledAt;

    private Instant expiresAt;

    protected Notification() {
    }

    public Notification(User tenant, String notificationType, String recipient, String message, String payload) {
        this(tenant, notificationType, recipient, message, payload, null);
    }

    public Notification(
            User tenant,
            String notificationType,
            String recipient,
            String message,
            String payload,
            Instant scheduledAt) {
        this.tenant = tenant;
        this.notificationType = notificationType;
        this.recipient = recipient;
        this.message = message;
        this.payload = payload;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.scheduledAt = scheduledAt;
    }

    public Long getId() {
        return id;
    }

    public User getTenant() {
        return tenant;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getMessage() {
        return message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        this.updatedAt = Instant.now();
    }

    public String getRecipient() {
        return recipient;
    }

    public String getPayload() {
        return payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void close(Instant closedAt) {
        this.closedAt = closedAt;
        this.updatedAt = closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPriority() {
        return priority;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
