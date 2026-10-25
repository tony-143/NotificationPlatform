package com.example.demo.services;

import com.example.demo.models.Notification;

public interface NotificationChannel {

    String type();

    void send(Notification notification);
}
