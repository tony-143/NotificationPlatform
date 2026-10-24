package com.example.demo.controllers;

import org.springframework.web.bind.annotation.RestController;

@RestController 
public class ApiKeyController {
    
    @PostMapping("/createApiKey")
    public String createApiKey() {
        return "API key created";
    }

    
}
