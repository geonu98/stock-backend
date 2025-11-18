package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.exception.TokenRefreshException;
import com.stock.dashboard.backend.model.UserDevice;
import com.stock.dashboard.backend.model.token.RefreshToken;
import com.stock.dashboard.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh.expiration}")
    private Long refreshTokenDurationMs; // application.properties에 추가할 예정

    /**
     * 새 Refresh Token 생성
     */
    public RefreshToken createRefreshToken(UserDevice userDevice) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserDevice(userDevice);
        refreshToken.setExpiryDt(Instant.now().plusMillis(refreshTokenDurationMs)); // ✅ 필드명 통일
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setRefreshCount(0L);
        refreshToken.setCreatedAt(LocalDateTime.now());  // ✅ DB default 없을 경우 대비
        refreshToken.setUpdatedAt(LocalDateTime.now());

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * 기존 UserDevice로 RefreshToken 찾기
     */
    public Optional<RefreshToken> findByUserDevice(UserDevice userDevice) {
        return refreshTokenRepository.findByUserDevice(userDevice);
    }

    /**
     * 토큰 유효성 검증
     */
    public Optional<RefreshToken> verifyExpiration(RefreshToken token) {
        if (token.getExpiryDt().isBefore(Instant.now())) { // ✅ 필드명 통일
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token expired. Please login again.");
        }
        return Optional.of(token);
    }

    /**
     * 토큰 문자열로 조회
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * 토큰 삭제
     */
    public void deleteByToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    /**
     * 토큰 수동 저장 (upsert용)
     */
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * 만료일 재계산 (AuthService에서 재사용)
     */
    public Instant generateExpiryDate() {
        return Instant.now().plusMillis(refreshTokenDurationMs);
    }
}
