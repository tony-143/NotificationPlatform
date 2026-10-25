package com.example.demo.services;

public class NotificationPermanentException extends RuntimeException {

    public NotificationPermanentException(String message) {
        super(message);
    }

    public NotificationPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
