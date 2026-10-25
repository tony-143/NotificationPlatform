package com.example.demo.controllers;

import com.example.demo.services.ApiKeyService;
import com.example.demo.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/api/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse createApiKey(Authentication authentication) {
        com.example.demo.models.User user = userService.getUserByEmail(authentication.getName());
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Email not verified. Please verify your email to generate API keys.");
        }
        ApiKeyService.IssuedApiKey issued = apiKeyService.createOrRotate(authentication.getName());
        return new ApiKeyResponse(
                issued.secret(),
                issued.prefix(),
                issued.createdAt().toString(),
                "Copy this key now. It cannot be retrieved later.");
    }

    public record ApiKeyResponse(String apiKey, String prefix, String createdAt, String message) {
    }
    
}
