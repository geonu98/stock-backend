package com.stock.dashboard.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CorsEnvLogRunner implements CommandLineRunner {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String rawEnv;

    @Override
    public void run(String... args) {
        log.info("[CORS] app.cors.allowed-origins={}", allowedOrigins);
        log.info("[CORS] raw env CORS_ALLOWED_ORIGINS={}", rawEnv);
    }
}
