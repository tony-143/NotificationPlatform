package com.example.demo.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@RestController 
public class NotificationController {
    
    @GetMapping("/notifications")
    public String getNotifications() {
        return "List of notifications";
    }

    @PostMapping("/notifications")
    public String createNotification() {
        return "Notification created";
    }

    
}
