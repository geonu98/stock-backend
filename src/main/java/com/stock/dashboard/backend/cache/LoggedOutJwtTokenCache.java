package com.stock.dashboard.backend.cache;

import com.stock.dashboard.backend.event.OnUserLogoutSuccessEvent;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoggedOutJwtTokenCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider tokenProvider;

    private static final String LOGOUT_TOKEN_PREFIX = "logout_token:";

    /**
     * 로그아웃된 토큰을 Redis에 저장 (TTL = JWT 만료 시간)
     */
    public void markLogoutEventForToken(OnUserLogoutSuccessEvent event) {
        String token = event.getToken();
        Date expiryDate = tokenProvider.getTokenExpiryFromJWT(token);
        long ttlMillis = expiryDate.getTime() - System.currentTimeMillis();

        if (ttlMillis <= 0) {
            log.warn("[RedisCache] 이미 만료된 토큰이라 저장하지 않음: {}", token);
            return;
        }

        try {
            String redisKey = LOGOUT_TOKEN_PREFIX + token;
            redisTemplate.opsForValue().set(redisKey, event, Duration.ofMillis(ttlMillis));
            log.info("[RedisCache] 로그아웃 토큰 저장: {} (TTL: {}초)", event.getUserIdentifier(), ttlMillis / 1000);
        } catch (Exception e) {
            log.error("[RedisCache] 로그아웃 토큰 Redis 저장 실패", e);
        }
    }

    /**
     * 해당 토큰이 로그아웃된 토큰인지 확인
     */
    public boolean isTokenLoggedOut(String authToken) {
        String redisKey = LOGOUT_TOKEN_PREFIX + authToken;
        Boolean exists = redisTemplate.hasKey(redisKey);
        log.debug("[RedisCache] 로그아웃 토큰 여부 확인: {} -> {}", redisKey, exists);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 토큰에 해당하는 로그아웃 이벤트 조회
     */
    public OnUserLogoutSuccessEvent getLogoutEventForToken(String authToken) {
        String redisKey = LOGOUT_TOKEN_PREFIX + authToken;
        Object result = redisTemplate.opsForValue().get(redisKey);
        return (result instanceof OnUserLogoutSuccessEvent) ? (OnUserLogoutSuccessEvent) result : null;
    }
}
