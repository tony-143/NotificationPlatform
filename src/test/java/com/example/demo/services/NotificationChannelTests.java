package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.demo.models.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class NotificationChannelTests {

    @Test
    void emailWithoutConfiguredProviderIsNotReportedAsSent() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EmailProvider> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        EmailService emailService = new EmailService(provider);
        Notification notification = new Notification(
                org.mockito.Mockito.mock(com.example.demo.models.User.class),
                "EMAIL",
                "recipient@example.test",
                "message",
                null);

        assertThrows(ProviderNotConfiguredException.class, () -> emailService.send(notification));
    }

    @Test
    void smsWithoutConfiguredProviderIsNotReportedAsSent() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SmsProvider> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        SMSService smsService = new SMSService(provider);
        Notification notification = new Notification(
                org.mockito.Mockito.mock(com.example.demo.models.User.class),
                "SMS",
                "+15555550100",
                "message",
                null);

        assertThrows(ProviderNotConfiguredException.class, () -> smsService.send(notification));
    }
}
