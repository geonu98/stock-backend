package com.stock.dashboard.backend.model.payload;

import com.stock.dashboard.backend.model.vo.DeviceType; // 🛑 경로 수정
import com.stock.dashboard.backend.validation.annotation.NullOrNotBlank; // 🛑 경로 수정

import jakarta.validation.constraints.NotBlank; // 🛑 Jakarta EE로 변경
import jakarta.validation.constraints.NotNull; // 🛑 Jakarta EE로 변경

public class DeviceInfo {

    @NotBlank(message = "Device id cannot be blank")
    private String deviceId;

    @NotNull(message = "Device type cannot be null")
    private DeviceType deviceType;

    @NullOrNotBlank(message = "Device notification token can be null but not blank")
    private String notificationToken;

    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, DeviceType deviceType, String notificationToken) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.notificationToken = notificationToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public String getNotificationToken() {
        return notificationToken;
    }

    public void setNotificationToken(String notificationToken) {
        this.notificationToken = notificationToken;
    }

    @Override
    public String toString() {
        return "DeviceInfo{" +
                "deviceId='" + deviceId + '\'' +
                ", deviceType=" + deviceType +
                ", notificationToken='" + notificationToken + '\'' +
                '}';
    }
}
