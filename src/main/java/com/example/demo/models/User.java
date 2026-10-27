package com.example.demo.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "email_verified")
    private Boolean emailVerified = Boolean.FALSE;

    @Column(name = "otp")
    @JsonIgnore
    private Long sixDigitOtp = null;

    @Column(name = "otp_purpose", length = 32)
    @JsonIgnore
    private String otpPurpose;

    @Column(name = "otp_expires_at")
    @JsonIgnore
    private Instant otpExpiresAt;

    protected User() {
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (emailVerified == null) {
            emailVerified = Boolean.FALSE;
        }
    }

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        if (this.emailVerified == null) {
            this.emailVerified = Boolean.FALSE;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean isEmailVerified() {
        return emailVerified == null ? Boolean.FALSE : emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified == null ? Boolean.FALSE : emailVerified;
    }

    public Long getSixDigitOtp() {
        return sixDigitOtp;
    }

    public void setSixDigitOtp(Long sixDigitOtp) {
        this.sixDigitOtp = sixDigitOtp;
    }

    public String getOtpPurpose() {
        return otpPurpose;
    }

    public void setOtpPurpose(String otpPurpose) {
        this.otpPurpose = otpPurpose;
    }

    public Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(Instant otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public void clearOtp() {
        this.sixDigitOtp = null;
        this.otpPurpose = null;
        this.otpExpiresAt = null;
    }
}
