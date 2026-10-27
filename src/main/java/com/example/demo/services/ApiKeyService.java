package com.example.demo.services;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.User;
import com.example.demo.repositeries.ApiKeyRepository;
import com.example.demo.repositeries.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "np_";
    private static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public IssuedApiKey createOrRotate(String email) {
        return createOrRotate(email, null);
    }

    @Transactional
    public IssuedApiKey createOrRotate(String email, String name) {
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String rawKey = generateKey();
        String prefix = rawKey.substring(0, 11);
        Instant createdAt = Instant.now();

        ApiKeys apiKey = apiKeyRepository.findByUser_Id(user.getId()).orElseGet(ApiKeys::new);
        apiKey.setUser(user);
        apiKey.setName(normalizeName(name));
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(Sha256Hasher.hash(rawKey));
        apiKey.setKeyStatus(ApiKeys.ACTIVE);
        apiKey.setCreatedAt(createdAt);
        apiKey.setExpiresAt(null);
        apiKey.setLastUsedAt(null);

        apiKeyRepository.save(apiKey);
        return new IssuedApiKey(apiKey.getId(), apiKey.getName(), rawKey, prefix, createdAt);
    }

    @Transactional
    public IssuedApiKey rotate(String email, Long keyId) {
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        ApiKeys apiKey = apiKeyRepository.findByIdAndUser_Id(keyId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        String rawKey = generateKey();
        String prefix = rawKey.substring(0, 11);
        Instant createdAt = Instant.now();

        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(Sha256Hasher.hash(rawKey));
        apiKey.setKeyStatus(ApiKeys.ACTIVE);
        apiKey.setCreatedAt(createdAt);
        apiKey.setLastUsedAt(null);
        apiKeyRepository.save(apiKey);

        return new IssuedApiKey(apiKey.getId(), apiKey.getName(), rawKey, prefix, createdAt);
    }

    @Transactional
    public void revoke(String email, Long keyId) {
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        ApiKeys apiKey = apiKeyRepository.findByIdAndUser_Id(keyId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        apiKey.setKeyStatus(ApiKeys.REVOKED);
        apiKeyRepository.save(apiKey);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeys> findForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return apiKeyRepository.findByUser_Id(user.getId());
    }

    @Transactional
    public ApiKeys authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > 128) {
            throw invalidApiKey();
        }
        ApiKeys apiKey = apiKeyRepository.findUsableKey(Sha256Hasher.hash(rawKey), ApiKeys.ACTIVE, Instant.now())
                .orElseThrow(ApiKeyService::invalidApiKey);

        Instant now = Instant.now();
        if (apiKey.getLastUsedAt() == null
                || apiKey.getLastUsedAt().isBefore(now.minus(LAST_USED_WRITE_INTERVAL))) {
            apiKey.setLastUsedAt(now);
            apiKeyRepository.save(apiKey);
        }
        return apiKey;
    }

    private String generateKey() {
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Default key";
        }
        String trimmed = name.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static ResponseStatusException invalidApiKey() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked API key");
    }

    public record IssuedApiKey(Long id, String name, String secret, String prefix, Instant createdAt) {
    }
}
