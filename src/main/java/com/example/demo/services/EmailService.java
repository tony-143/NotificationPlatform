package com.example.demo.services;

import com.example.demo.models.Notification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EmailService implements NotificationChannel {

    private final ObjectProvider<EmailProvider> provider;

    public EmailService(ObjectProvider<EmailProvider> provider) {
        this.provider = provider;
    }

    @Override
    public String type() {
        return NotificationType.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        sendEmail(notification.getRecipient(), "Notification", notification.getMessage());
    }

    public void sendEmail(String recipient, String subject, String message) {
        EmailProvider configuredProvider = provider.getIfAvailable();
        if (configuredProvider == null) {
            throw new ProviderNotConfiguredException("Email provider is not configured");
        }
        try {
            configuredProvider.send(recipient, subject, message);
        } catch (ProviderNotConfiguredException exception) {
            throw exception;
        } catch (NotificationPermanentException exception) {
            throw exception;
        } catch (NotificationDeliveryException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new NotificationPermanentException("Email request was rejected as invalid", exception);
        } catch (RuntimeException exception) {
            throw new NotificationDeliveryException("Email provider failed to deliver notification", exception);
        }
    }
}
