package com.stock.dashboard.backend.model.payload.response;

import java.time.Instant;

public class UserListResponse {

    private Long userId;
    private String email;
    private String username;
    private Boolean active;
    private Boolean emailActive;
    private Instant createdAt;

    public UserListResponse() {}

    public UserListResponse(Long userId, String email, String username, Boolean active, Boolean emailActive, Instant createdAt) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.active = active;
        this.emailActive = emailActive;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getEmailActive() {
        return emailActive;
    }

    public void setEmailActive(Boolean emailActive) {
        this.emailActive = emailActive;
    }
}
