package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.UserDevice;
import com.stock.dashboard.backend.model.vo.DeviceType;
import com.stock.dashboard.backend.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;

    /**
     * 유저 + 디바이스ID 기반으로 기기를 조회 후 없으면 신규 생성
     *  로그인 시 deviceId와 deviceType을 받아서

     * 기존 기기면 상태 갱신 (isRefreshActive = true)

     * 새 기기면 신규 저장
     */
    public UserDevice createOrUpdateDeviceInfo(User user, String deviceId, DeviceType deviceType) {
        Optional<UserDevice> existingDeviceOpt = userDeviceRepository.findByUserAndDeviceId(user, deviceId);

        if (existingDeviceOpt.isPresent()) {
            UserDevice existingDevice = existingDeviceOpt.get();
            existingDevice.setDeviceType(deviceType);
            existingDevice.setIsRefreshActive(true);
            log.info("[UserDeviceService] 기존 기기 정보 갱신: {}", deviceId);
            return userDeviceRepository.save(existingDevice);
        }

        // 새 기기 등록
        UserDevice newDevice = new UserDevice();
        newDevice.setUser(user);
        newDevice.setDeviceId(deviceId);
        newDevice.setDeviceType(deviceType);
        newDevice.setIsRefreshActive(true);
 /*
 @EnableJpaAuditing (→ @CreatedDate, @LastModifiedDate 처리)

DateAudit의 @PrePersist, @PreUpdate

DB 컬럼의 DEFAULT CURRENT_TIMESTAMP + ON UPDATE CURRENT_TIMESTAMP

즉,

JPA가 새 엔티티 저장 시 createdAt, updatedAt 자동 채움

업데이트 시 updatedAt 자동 갱신

만약 JPA가 누락해도 DB가 보정
  */

        log.info("[UserDeviceService] 새 기기 등록: {}", deviceId);
        return userDeviceRepository.save(newDevice);
    }
}
