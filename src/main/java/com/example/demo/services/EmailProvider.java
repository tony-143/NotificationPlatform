package com.example.demo.services;

public interface EmailProvider {

    void send(String recipient, String subject, String message);

    default void send(String recipient, String message) {
        send(recipient, "Notification", message);
    }
}
