package com.example.demo.controllers;

import com.example.demo.services.DashboardService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only analytics for the signed-in user. Every query is scoped to the
 * tenant resolved from the session, never from a client-supplied id.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardService.SummaryView summary(
            Authentication authentication,
            @RequestParam(defaultValue = "7d") String range) {
        return dashboardService.summary(authentication.getName(), range);
    }

    @GetMapping("/timeseries")
    public DashboardService.TimeSeriesView timeseries(
            Authentication authentication,
            @RequestParam(defaultValue = "7d") String range) {
        return dashboardService.series(authentication.getName(), range);
    }

    @GetMapping("/notifications")
    public DashboardService.PageView notifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, name = "q") String search) {
        return dashboardService.history(
                authentication.getName(),
                page,
                size,
                status,
                channel,
                parseInstant(from, "from"),
                parseInstant(to, "to"),
                search);
    }

    @GetMapping("/notifications/{notificationId}")
    public DashboardService.NotificationDetail notification(
            Authentication authentication,
            @PathVariable Long notificationId) {
        return dashboardService.detail(authentication.getName(), notificationId);
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " must be an ISO-8601 instant such as 2026-01-01T00:00:00Z");
        }
    }
}
