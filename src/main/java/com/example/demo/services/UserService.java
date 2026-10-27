package com.example.demo.services;

import com.example.demo.models.User;
import com.example.demo.repositeries.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String PASSWORD_RESET = "PASSWORD_RESET";
    private static final Duration OTP_LIFETIME = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalizedName = requireValue(name, "Name");
        String normalizedEmail = normalizeEmail(email);
        validatePassword(rawPassword);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered please verify is not verified!");
        }

        User user = new User(normalizedName, normalizedEmail, passwordEncoder.encode(rawPassword));
        user.setEmailVerified(false);
        User saved = userRepository.save(user);
        sendOtp(saved, EMAIL_VERIFICATION, "Verify your email address");
        return saved;
    }

    public User login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (rawPassword == null || rawPassword.isBlank()) {
            throw invalidCredentials();
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(UserService::invalidCredentials);
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw invalidCredentials();
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not verified. Please verify your email.");
        }
        return user;
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void resetPasswordWithOtp(String email, Long otp, String newPassword) {
        validatePassword(newPassword);
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(UserService::invalidOtp);
        validateOtp(user, otp, PASSWORD_RESET);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.clearOtp();
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already verified");
        }
        sendOtp(user, EMAIL_VERIFICATION, "Verify your email address");
    }

    @Transactional
    public void verifyEmail(String email, Long otp) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(UserService::invalidOtp);
        validateOtp(user, otp, EMAIL_VERIFICATION);
        user.setEmailVerified(true);
        user.clearOtp();
    }

    @Transactional
    public void sendPasswordResetOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        userRepository.findByEmail(normalizedEmail)
                .filter(User::isEmailVerified)
                .ifPresent(user -> sendOtp(user, PASSWORD_RESET, "Reset your password"));
    }

    private void sendOtp(User user, String purpose, String subject) {
        long otp = OTP_RANDOM.nextInt(900_000) + 100_000L;
        user.setSixDigitOtp(otp);
        user.setOtpPurpose(purpose);
        user.setOtpExpiresAt(Instant.now().plus(OTP_LIFETIME));
        userRepository.save(user);
        emailService.sendEmail(
                user.getEmail(),
                subject,
                "Your verification code is " + otp + ". It expires in 10 minutes.");
    }

    private void validateOtp(User user, Long suppliedOtp, String expectedPurpose) {
        if (suppliedOtp == null
                || suppliedOtp < 100_000
                || suppliedOtp > 999_999
                || !Objects.equals(user.getSixDigitOtp(), suppliedOtp)
                || !expectedPurpose.equals(user.getOtpPurpose())
                || user.getOtpExpiresAt() == null
                || !user.getOtpExpiresAt().isAfter(Instant.now())) {
            throw invalidOtp();
        }
    }

    private static ResponseStatusException invalidOtp() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = requireValue(email, "Email").toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be valid");
        }
        return normalizedEmail;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
