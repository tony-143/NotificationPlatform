package com.example.demo.controllers;

import com.example.demo.models.User;
import com.example.demo.services.JwtService;
import com.example.demo.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final boolean secureAuthCookie;

    public UserController(
            UserService userService,
            JwtService jwtService,
            @Value("${app.jwt.cookie-secure:false}") boolean secureAuthCookie) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.secureAuthCookie = secureAuthCookie;
    }

    @GetMapping("/api/users")
    public List<User> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/api/users/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody RegistrationRequest request) {
        User user = userService.register(request.name(), request.email(), request.password());
        return new RegisterResponse(user.getId(), user.getEmail(), "Registration successful. Please check your email for verification code.");
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        User user = userService.login(request.email(), request.password());
        String token = jwtService.generateToken(user);
        response.addHeader("Set-Cookie", authCookie(token, jwtService.getExpirationSeconds()).toString());
        return new LoginResponse(user.getId(), user.getName(), user.getEmail());
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        response.addHeader("Set-Cookie", authCookie("", 0).toString());
    }

    @PostMapping("/api/auth/verify-email")
    @ResponseStatus(HttpStatus.OK)
    public MessageResponse verifyEmail(@RequestBody VerifyOtpRequest request) {
        userService.verifyEmail(request.email(), request.otp());
        return new MessageResponse("Email verified successfully");
    }

    @PostMapping("/api/auth/resend-verification")
    @ResponseStatus(HttpStatus.OK)
    public MessageResponse resendVerification(@RequestBody EmailRequest request) {
        userService.resendVerification(request.email());
        return new MessageResponse("Verification code sent to your email");
    }

    @PostMapping("/api/auth/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public MessageResponse forgotPassword(@RequestBody EmailRequest request) {
        userService.sendPasswordResetOtp(request.email());
        return new MessageResponse("If an account exists with this email, a password reset code has been sent");
    }

    @PostMapping("/api/auth/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPasswordWithOtp(request.email(), request.otp(), request.newPassword());
        return new MessageResponse("Password reset successfully");
    }

    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PutMapping("/api/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @RequestBody PasswordChangeRequest request) {
        userService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword());
    }

    public record RegistrationRequest(String name, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    public record VerifyOtpRequest(String email, Long otp) {
    }

    public record EmailRequest(String email) {
    }

    public record ResetPasswordRequest(String email, Long otp, String newPassword) {
    }

    public record LoginResponse(
            Long userId,
            String name,
            String email) {
    }

    public record RegisterResponse(Long userId, String email, String message) {
    }

    public record CsrfResponse(String token, String headerName) {
    }

    public record MessageResponse(String message) {
    }

    private ResponseCookie authCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("ACCESS_TOKEN", token)
                .httpOnly(true)
                .secure(secureAuthCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
