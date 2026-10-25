package com.example.demo.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;

    public SmtpEmailProvider(
            JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:0}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipient, String message) {
        if (host.isBlank() || port < 1 || username.isBlank() || password.isBlank() || fromAddress.isBlank()) {
            throw new ProviderNotConfiguredException(
                    "SMTP provider requires host, port, username, password, and sender address");
        }

        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(recipient);
        email.setText(message);
        email.setSubject("Notification");

        try {
            mailSender.send(email);
        } catch (MailAuthenticationException | MailParseException exception) {
            throw new NotificationPermanentException("SMTP rejected the credentials or email request", exception);
        } catch (MailException exception) {
            throw new NotificationDeliveryException("SMTP provider failed to deliver email", exception);
        }
    }
}
