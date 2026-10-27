package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.User;
import com.example.demo.repositeries.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, emailService);
    }

    @Test
    void registrationStoresSixDigitVerificationOtpAndSendsItWithEmailService() {
        when(passwordEncoder.encode("password123")).thenReturn("encoded:password123");
        when(userRepository.existsByEmail("person@example.test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register(" Person ", " PERSON@EXAMPLE.TEST ", "password123");

        assertEquals("person@example.test", user.getEmail());
        assertFalse(user.isEmailVerified());
        assertNotNull(user.getSixDigitOtp());
        assertTrue(user.getSixDigitOtp() >= 100_000 && user.getSixDigitOtp() <= 999_999);
        assertEquals("EMAIL_VERIFICATION", user.getOtpPurpose());
        assertTrue(user.getOtpExpiresAt().isAfter(Instant.now()));
        verify(emailService).sendEmail(
                eq("person@example.test"),
                eq("Verify your email address"),
                eq("Your verification code is " + user.getSixDigitOtp() + ". It expires in 10 minutes."));
    }

    @Test
    void verificationRequiresValidOtpAndClearsItAfterSuccess() {
        User user = user("person@example.test", false, 123456L, "EMAIL_VERIFICATION");
        when(userRepository.findByEmail("person@example.test")).thenReturn(Optional.of(user));

        userService.verifyEmail("person@example.test", 123456L);

        assertTrue(user.isEmailVerified());
        assertNull(user.getSixDigitOtp());
        assertNull(user.getOtpPurpose());
        assertNull(user.getOtpExpiresAt());
    }

    @Test
    void expiredOrWrongPurposeOtpCannotVerifyEmail() {
        User user = user("person@example.test", false, 123456L, "PASSWORD_RESET");
        user.setOtpExpiresAt(Instant.now().minusSeconds(1));
        when(userRepository.findByEmail("person@example.test")).thenReturn(Optional.of(user));

        assertThrows(
                ResponseStatusException.class,
                () -> userService.verifyEmail("person@example.test", 123456L));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void resendVerificationRotatesAndEmailsOtp() {
        User user = user("person@example.test", false, 111111L, "EMAIL_VERIFICATION");
        when(userRepository.findByEmail("person@example.test")).thenReturn(Optional.of(user));

        userService.resendVerification("person@example.test");

        assertNotNull(user.getSixDigitOtp());
        assertEquals("EMAIL_VERIFICATION", user.getOtpPurpose());
        verify(userRepository).save(user);
        verify(emailService).sendEmail(eq(user.getEmail()), eq("Verify your email address"), any());
    }

    @Test
    void forgotPasswordDoesNotSendEmailForUnknownOrUnverifiedUser() {
        when(userRepository.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        userService.sendPasswordResetOtp("missing@example.test");

        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void forgotPasswordEmailsOtpOnlyForVerifiedAccount() {
        User user = user("person@example.test", true, null, null);
        when(userRepository.findByEmail("person@example.test")).thenReturn(Optional.of(user));

        userService.sendPasswordResetOtp("person@example.test");

        assertEquals("PASSWORD_RESET", user.getOtpPurpose());
        assertNotNull(user.getSixDigitOtp());
        verify(emailService).sendEmail(eq(user.getEmail()), eq("Reset your password"), any());
    }

    @Test
    void passwordResetRequiresMatchingOtpAndChangesPasswordThenConsumesOtp() {
        when(passwordEncoder.encode("newPassword123")).thenReturn("encoded:newPassword123");
        User user = user("person@example.test", true, 123456L, "PASSWORD_RESET");
        when(userRepository.findByEmail("person@example.test")).thenReturn(Optional.of(user));

        userService.resetPasswordWithOtp("person@example.test", 123456L, "newPassword123");

        assertEquals("encoded:newPassword123", user.getPassword());
        assertNull(user.getSixDigitOtp());
        assertNull(user.getOtpPurpose());
        assertNull(user.getOtpExpiresAt());
    }

    @Test
    void passwordResetRejectsWrongOtpWithoutChangingPassword() {
        User user = user("person@example.test", true, 123456L, "PASSWORD_RESET");
        when(userRepository.findByEmail("person@example.test")).thenReturn(Optional.of(user));

        assertThrows(
                ResponseStatusException.class,
                () -> userService.resetPasswordWithOtp("person@example.test", 654321L, "newPassword123"));
        assertEquals("encoded:initialPassword", user.getPassword());
    }

    private User user(String email, boolean verified, Long otp, String purpose) {
        User user = new User("Person", email, "encoded:initialPassword");
        user.setEmailVerified(verified);
        user.setSixDigitOtp(otp);
        user.setOtpPurpose(purpose);
        user.setOtpExpiresAt(otp == null ? null : Instant.now().plusSeconds(600));
        return user;
    }
}
