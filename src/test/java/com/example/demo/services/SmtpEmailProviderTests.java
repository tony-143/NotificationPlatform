package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.example.demo.models.Notification;
import com.example.demo.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

class SmtpEmailProviderTests {

    @Test
    void sendsEmailUsingConfiguredFromRecipientAndBody() {
        JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
        SmtpEmailProvider provider = new SmtpEmailProvider(
                sender,
                "smtp.example.test",
                587,
                "sender@example.test",
                "test-app-password",
                "sender@example.test");
        Notification notification = new Notification(
                org.mockito.Mockito.mock(User.class),
                "EMAIL",
                "recipient@example.test",
                "Hello from the notification service",
                null);

        provider.send(notification.getRecipient(), notification.getMessage());

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("sender@example.test", message.getValue().getFrom());
        assertEquals("recipient@example.test", message.getValue().getTo()[0]);
        assertEquals("Notification", message.getValue().getSubject());
        assertEquals("Hello from the notification service", message.getValue().getText());
    }

    @Test
    void reportsMissingSmtpSettingsAsNotConfigured() {
        JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
        SmtpEmailProvider provider = new SmtpEmailProvider(sender, "", 0, "", "", "");

        assertThrows(
                ProviderNotConfiguredException.class,
                () -> provider.send("recipient@example.test", "message"));
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @Test
    void classifiesSmtpAuthenticationFailureAsPermanent() {
        JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new MailAuthenticationException("authentication rejected"))
                .when(sender)
                .send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        SmtpEmailProvider provider = new SmtpEmailProvider(
                sender,
                "smtp.example.test",
                587,
                "sender@example.test",
                "bad-password",
                "sender@example.test");

        assertThrows(
                NotificationPermanentException.class,
                () -> provider.send("recipient@example.test", "message"));
    }
}
