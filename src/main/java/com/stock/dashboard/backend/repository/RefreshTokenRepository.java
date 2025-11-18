package com.stock.dashboard.backend.repository;

import com.stock.dashboard.backend.model.UserDevice;
import com.stock.dashboard.backend.model.token.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
//“토큰으로 찾기”
    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    // ✅ 추가: UserDevice 기준 조회 (기기별 1개 RefreshToken 정책)
    // “기기로 찾기”
    Optional<RefreshToken> findByUserDevice(UserDevice userDevice);
}
