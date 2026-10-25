package com.example.demo.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "notification_idempotency", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key_hash", columnNames = {"api_key_id", "idempotency_hash"})
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKeys apiKey;

    @Column(name = "idempotency_hash", nullable = false, length = 64)
    private String idempotencyHash;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, unique = true)
    private Notification notification;

    @Column(nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(
            ApiKeys apiKey,
            String idempotencyHash,
            String requestHash,
            Notification notification,
            Instant expiresAt) {
        this.apiKey = apiKey;
        this.idempotencyHash = idempotencyHash;
        this.requestHash = requestHash;
        this.notification = notification;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public ApiKeys getApiKey() {
        return apiKey;
    }

    public String getIdempotencyHash() {
        return idempotencyHash;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Notification getNotification() {
        return notification;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
