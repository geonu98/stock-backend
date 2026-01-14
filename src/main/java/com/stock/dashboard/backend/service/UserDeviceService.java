package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.UserDevice;
import com.stock.dashboard.backend.repository.UserDeviceRepository;
import com.stock.dashboard.backend.validation.validator.DeviceTypeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;

    public UserDevice createOrUpdateDeviceInfo(User user, String deviceId, String deviceType) {
        Optional<UserDevice> existingDeviceOpt =
                userDeviceRepository.findByUserAndDeviceId(user, deviceId);

        //  여기서 무조건 표준화/검증 끝
        String normalizedDeviceType = DeviceTypeValidator.normalizeOrThrow(deviceType);

        if (existingDeviceOpt.isPresent()) {
            UserDevice existingDevice = existingDeviceOpt.get();
            existingDevice.setDeviceType(normalizedDeviceType);
            existingDevice.setIsRefreshActive(true);

            log.info("[UserDeviceService] 기존 기기 정보 갱신: deviceId={}, deviceType={}",
                    deviceId, normalizedDeviceType);

            return userDeviceRepository.save(existingDevice);
        }

        UserDevice newDevice = new UserDevice();
        newDevice.setUser(user);
        newDevice.setDeviceId(deviceId);
        newDevice.setDeviceType(normalizedDeviceType);
        newDevice.setIsRefreshActive(true);

        log.info("[UserDeviceService] 새 기기 등록: deviceId={}, deviceType={}",
                deviceId, normalizedDeviceType);

        return userDeviceRepository.save(newDevice);
    }
}
