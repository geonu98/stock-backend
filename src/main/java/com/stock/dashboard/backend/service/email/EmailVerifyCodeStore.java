package com.stock.dashboard.backend.service.email;

import com.stock.dashboard.backend.service.email.dto.VerifyCodePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailVerifyCodeStore {

    private final RedisTemplate<String, Object> redisTemplate;

    // 5분 정도면 충분 (원하면 10분도 OK)
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "email_verify_code:";

    public String issueCode(VerifyCodePayload payload) {
        String code = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PREFIX + code, payload, TTL);
        return code;
    }

    public VerifyCodePayload consumeCode(String code) {
        String key = PREFIX + code;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) return null;

        // 1회용: 읽자마자 삭제
        redisTemplate.delete(key);
        return (VerifyCodePayload) obj;
    }
}
