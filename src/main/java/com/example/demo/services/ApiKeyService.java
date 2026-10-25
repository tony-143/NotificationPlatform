package com.example.demo.services;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.User;
import com.example.demo.repositeries.ApiKeyRepository;
import com.example.demo.repositeries.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "np_";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public IssuedApiKey createOrRotate(String email) {
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String rawKey = generateKey();
        String prefix = rawKey.substring(0, 11);
        Instant createdAt = Instant.now();

        ApiKeys apiKey = apiKeyRepository.findByUser_Id(user.getId()).orElseGet(ApiKeys::new);
        apiKey.setUser(user);
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(Sha256Hasher.hash(rawKey));
        apiKey.setKeyStatus(ApiKeys.ACTIVE);
        apiKey.setCreatedAt(createdAt);
        apiKey.setExpiresAt(null);

        apiKeyRepository.save(apiKey);
        return new IssuedApiKey(rawKey, prefix, createdAt);
    }

    @Transactional(readOnly = true)
    public ApiKeys authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > 128) {
            throw invalidApiKey();
        }
        return apiKeyRepository.findUsableKey(Sha256Hasher.hash(rawKey), ApiKeys.ACTIVE, Instant.now())
                .orElseThrow(ApiKeyService::invalidApiKey);
    }

    private String generateKey() {
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
    }

    private static ResponseStatusException invalidApiKey() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked API key");
    }

    public record IssuedApiKey(String secret, String prefix, Instant createdAt) {
    }
}
