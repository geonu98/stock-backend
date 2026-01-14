package com.stock.dashboard.backend.exception;

public class EmailRequiredException extends RuntimeException {

    private final String provider;
    private final String providerId;

    public EmailRequiredException(String provider, String providerId) {
        super("EMAIL_REQUIRED");
        this.provider = provider;
        this.providerId = providerId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }
}
