package com.example.demo.controllers;

import com.example.demo.models.User;
import com.example.demo.services.JwtService;
import com.example.demo.services.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    public UserController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
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
    public User register(@RequestBody RegistrationRequest request) {
        return userService.register(request.name(), request.email(), request.password());
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());
        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                user.getName(),
                user.getEmail());
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

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            Long userId,
            String name,
            String email) {
    }
}
