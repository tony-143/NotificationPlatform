package com.example.demo.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity 
@Table(name = "api_keys")
public class ApiKeys {
    
    //  add forieng key to user table

    private Long id;

    private Long userId;

    private String keyPrefix;

    private String keyHash;

    private String keyStatus;

    private String createdAt;

    private String expiresAt;
    

}
