package com.stock.dashboard.backend.exception;

public record ErrorResponse(
        String code,
        String message
) {}
