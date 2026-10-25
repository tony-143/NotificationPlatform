package com.example.demo.services;

public interface SmsProvider {

    void send(String recipient, String message);
}
