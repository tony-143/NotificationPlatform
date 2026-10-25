package com.example.demo.services;

import com.example.demo.models.Notification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SMSService implements NotificationChannel {

    private final ObjectProvider<SmsProvider> provider;

    public SMSService(ObjectProvider<SmsProvider> provider) {
        this.provider = provider;
    }

    @Override
    public String type() {
        return NotificationType.SMS;
    }

    @Override
    public void send(Notification notification) {
        SmsProvider configuredProvider = provider.getIfAvailable();
        if (configuredProvider == null) {
            throw new ProviderNotConfiguredException("SMS provider is not configured");
        }
        try {
            configuredProvider.send(notification.getRecipient(), notification.getMessage());
        } catch (ProviderNotConfiguredException | NotificationPermanentException | NotificationDeliveryException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new NotificationPermanentException("SMS request was rejected as invalid", exception);
        } catch (RuntimeException exception) {
            throw new NotificationDeliveryException("SMS provider failed to deliver notification", exception);
        }
    }
}
