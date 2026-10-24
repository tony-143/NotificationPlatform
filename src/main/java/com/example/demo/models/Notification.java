package com.example.demo.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity 
@Table (name = "notifications")
public class Notification {
    
    private Long id;

    private Long tenantId;

    private String notification_type;

    private String message;

    private String status;

    private String recipient;

    private String payload;

    private int retryCount;

    private String createdAt;

    private String updatedAt;

    private String priority;

    private String scheduledAt;

    private String expiresAt;

    
}
