package com.example.demo.services;

import com.example.demo.models.Notification;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NotificationAdaptor {

    private final Map<String, NotificationChannel> channels;

    public NotificationAdaptor(java.util.List<NotificationChannel> channels) {
        this.channels = channels.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationChannel::type, Function.identity()));
    }

    public void send(Notification notification) {
        NotificationChannel channel = channels.get(notification.getNotificationType());
        if (channel == null) {
            throw new NotificationPermanentException(
                    "Unsupported notification type: " + notification.getNotificationType());
        }
        channel.send(notification);
    }
}
