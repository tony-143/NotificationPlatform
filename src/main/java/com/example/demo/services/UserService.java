package com.example.demo.services;

import com.example.demo.models.User;
import com.example.demo.repositeries.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User register(String name, String email, String rawPassword) {
        String normalizedName = requireValue(name, "Name");
        String normalizedEmail = normalizeEmail(email);
        validatePassword(rawPassword);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User(normalizedName, normalizedEmail, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
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
