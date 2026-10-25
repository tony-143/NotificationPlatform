package com.example.demo.controllers;

import com.example.demo.services.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/api/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse createApiKey(Authentication authentication) {
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
