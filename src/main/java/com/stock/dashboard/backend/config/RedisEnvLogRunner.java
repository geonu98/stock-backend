package com.stock.dashboard.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisEnvLogRunner {

    private final Environment env;

    public RedisEnvLogRunner(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logRedisConfig() {
        log.info("spring.data.redis.url={}", env.getProperty("spring.data.redis.url"));
        log.info("spring.data.redis.host={}", env.getProperty("spring.data.redis.host"));
        log.info("spring.data.redis.port={}", env.getProperty("spring.data.redis.port"));
    }
}
