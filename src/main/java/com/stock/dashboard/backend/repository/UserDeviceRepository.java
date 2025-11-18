package com.stock.dashboard.backend.repository;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
/*
 특정 유저 + 기기 ID 기준으로 기존 기기를 찾기 위한 메서드.
이미 등록된 기기라면 “새 토큰 갱신”, 없으면 “신규 등록”.
 */
@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserAndDeviceId(User user, String deviceId);
}
