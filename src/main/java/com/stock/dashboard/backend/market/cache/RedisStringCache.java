package com.stock.dashboard.backend.market.cache;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisStringCache {

    private final StringRedisTemplate redis;

    private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT;
    static {
        DELETE_IF_MATCHES_SCRIPT = new DefaultRedisScript<>();
        DELETE_IF_MATCHES_SCRIPT.setResultType(Long.class);
        DELETE_IF_MATCHES_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "  return redis.call('del', KEYS[1]) " +
                        "else " +
                        "  return 0 " +
                        "end"
        );
    }

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

    //  추가: value가 일치할 때만 삭제 (락 안전 해제)
    public boolean deleteIfValueMatches(String key, String expectedValue) {
        Long res = redis.execute(DELETE_IF_MATCHES_SCRIPT, List.of(key), expectedValue);
        return res != null && res > 0;
    }
}