package com.example.demo;

import com.example.demo.models.ApiKeys;
import com.example.demo.services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public ApiKeyAuthenticationFilter(
            ApiKeyService apiKeyService,
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.apiKeyService = apiKeyService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !requestUri.equals("/api/notifications")
                && !requestUri.startsWith("/api/notifications/")
                && !requestUri.equals("/api/v1/notifications")
                && !requestUri.startsWith("/api/v1/notifications/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ApiKeys apiKey = apiKeyService.authenticate(request.getHeader("X-API-Key"));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(apiKey, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
        }
    }
}
