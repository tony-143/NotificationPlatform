package com.example.demo.services;

import com.example.demo.models.EmailVerificationToken;
import com.example.demo.models.PasswordResetToken;
import com.example.demo.models.User;
import com.example.demo.repositeries.EmailVerificationTokenRepository;
import com.example.demo.repositeries.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(10);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailOtpService(
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Transactional
    public void sendEmailVerification(User user) {
        emailVerificationTokenRepository.invalidateAllForUser(user.getId());
        String rawToken = generateToken();
        String tokenHash = hash(rawToken);
        EmailVerificationToken token = new EmailVerificationToken(user, tokenHash, Instant.now().plus(OTP_VALIDITY));
        emailVerificationTokenRepository.save(token);
        sendEmail(user.getEmail(), "Verify your email", "Your verification code is: " + rawToken + "\nThis code expires in 10 minutes.");
    }

    @Transactional
    public void sendPasswordReset(User user) {
        passwordResetTokenRepository.invalidateAllForUser(user.getId());
        String rawToken = generateToken();
        String tokenHash = hash(rawToken);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(OTP_VALIDITY));
        passwordResetTokenRepository.save(token);
        sendEmail(user.getEmail(), "Reset your password", "Your password reset code is: " + rawToken + "\nThis code expires in 10 minutes.");
    }

    @Transactional
    public User verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token is required");
        }
        String tokenHash = hash(rawToken.trim());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification token"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setUsed(true);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token has expired");
        }
        User user = token.getUser();
        user.setEmailVerified(true);
        token.setUsed(true);
        return user;
    }

    @Transactional
    public User verifyPasswordReset(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required");
        }
        String tokenHash = hash(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashAndUsedFalse(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setUsed(true);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token has expired");
        }
        token.setUsed(true);
        return token.getUser();
    }

    private void sendEmail(String to, String subject, String text) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception exception) {
            // Ignore email sending failures
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(12);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
