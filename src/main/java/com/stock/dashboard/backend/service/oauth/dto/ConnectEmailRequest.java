package com.stock.dashboard.backend.service.oauth.dto;

public record ConnectEmailRequest(
        String provider,
        String providerId,
        String email
) {}
