package com.stock.dashboard.backend.exception;



public class TwelveDataRateLimitException extends RuntimeException {
    public TwelveDataRateLimitException(String message) {
        super(message);
    }
}
