package com.stock.dashboard.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisEnvLogRunner implements CommandLineRunner {

    private final Environment env;

    public RedisEnvLogRunner(Environment env) {
        this.env = env;
    }

    @Override
    public void run(String... args) {
        log.info("Resolved spring.data.redis.url={}", env.getProperty("spring.data.redis.url"));
        log.info("Resolved spring.data.redis.host={}", env.getProperty("spring.data.redis.host"));
        log.info("Resolved spring.data.redis.port={}", env.getProperty("spring.data.redis.port"));
    }
}
