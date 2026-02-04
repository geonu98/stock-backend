package com.stock.dashboard.backend.market.cache;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisStringCache {

    private final StringRedisTemplate redis;

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        return redis.opsForValue().setIfAbsent(key, value, ttl);
    }

    public void delete(String key) {
        redis.delete(key);
    }
}
