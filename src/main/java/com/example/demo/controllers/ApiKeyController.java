package com.example.demo.controllers;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.User;
import com.example.demo.services.ApiKeyService;
import com.example.demo.services.UserService;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    public ApiKeyController(ApiKeyService apiKeyService, UserService userService) {
        this.apiKeyService = apiKeyService;
        this.userService = userService;
    }

    /**
     * Returns credential metadata only. The stored hash and the original secret
     * are never returned here (the secret exists only in the creation response).
     */
    @GetMapping("/api/keys")
    public ResponseEntity<ApiKeyResponse> getApiKey(Authentication authentication) {
        return apiKeyService.findForUser(authentication.getName())
                .map(key -> ResponseEntity.ok(ApiKeyResponse.from(key)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedApiKeyResponse createApiKey(
            Authentication authentication,
            @RequestBody(required = false) CreateApiKeyRequest request) {
        requireVerified(authentication.getName());
        ApiKeyService.IssuedApiKey issued = apiKeyService.createOrRotate(
                authentication.getName(),
                request == null ? null : request.name());
        return IssuedApiKeyResponse.from(issued);
    }

    @PostMapping("/api/keys/{keyId}/rotate")
    public IssuedApiKeyResponse rotateApiKey(
            Authentication authentication,
            @PathVariable Long keyId) {
        requireVerified(authentication.getName());
        return IssuedApiKeyResponse.from(apiKeyService.rotate(authentication.getName(), keyId));
    }

    @PostMapping("/api/keys/{keyId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(Authentication authentication, @PathVariable Long keyId) {
        apiKeyService.revoke(authentication.getName(), keyId);
    }

    private void requireVerified(String email) {
        User user = userService.getUserByEmail(email);
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Email not verified. Please verify your email to generate API keys.");
        }
    }

    public record CreateApiKeyRequest(String name) {
    }

    public record ApiKeyResponse(
            Long id,
            String name,
            String prefix,
            String status,
            String createdAt,
            String lastUsedAt,
            String expiresAt) {

        static ApiKeyResponse from(ApiKeys key) {
            return new ApiKeyResponse(
                    key.getId(),
                    key.getName(),
                    key.getKeyPrefix(),
                    key.getKeyStatus(),
                    key.getCreatedAt() == null ? null : key.getCreatedAt().toString(),
                    key.getLastUsedAt() == null ? null : key.getLastUsedAt().toString(),
                    key.getExpiresAt() == null ? null : key.getExpiresAt().toString());
        }
    }

    public record IssuedApiKeyResponse(
            Long id,
            String name,
            String apiKey,
            String prefix,
            String status,
            String createdAt,
            String message) {

        static IssuedApiKeyResponse from(ApiKeyService.IssuedApiKey issued) {
            Instant createdAt = issued.createdAt();
            return new IssuedApiKeyResponse(
                    issued.id(),
                    issued.name(),
                    issued.secret(),
                    issued.prefix(),
                    ApiKeys.ACTIVE,
                    createdAt == null ? null : createdAt.toString(),
                    "Copy this key now. It cannot be retrieved later.");
        }
    }
}
