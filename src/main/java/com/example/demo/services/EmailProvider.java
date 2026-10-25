package com.example.demo.services;

public interface EmailProvider {

    void send(String recipient, String message);
}
