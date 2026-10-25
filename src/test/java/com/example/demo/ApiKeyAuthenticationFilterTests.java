package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.ApiKeys;
import com.example.demo.services.ApiKeyService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTests {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private AuthenticationEntryPoint authenticationEntryPoint;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void installsAuthenticatedApiKeyAsPrincipalForVersionedNotificationRoute() throws Exception {
        ApiKeys apiKey = org.mockito.Mockito.mock(ApiKeys.class);
        when(apiKeyService.authenticate("np_test-key")).thenReturn(apiKey);
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService, authenticationEntryPoint);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications");
        request.addHeader("X-API-Key", "np_test-key");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertSame(apiKey, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void rejectsInvalidApiKey() throws ServletException, IOException {
        when(apiKeyService.authenticate("invalid"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService, authenticationEntryPoint);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/notifications");
        request.addHeader("X-API-Key", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any());
    }
}
