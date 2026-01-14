package com.stock.dashboard.backend.model.audit;


import com.stock.dashboard.backend.validation.annotation.NullOrNotBlank; //  경로 수정

import jakarta.validation.constraints.NotBlank; //  Jakarta EE로 변경


public class DeviceInfo {

    @NotBlank(message = "Device id cannot be blank")
    private String deviceId;

    /**
     * 외부에서 입력받는 디바이스 타입
     * - WEB / ANDROID / IOS / WINDOWS / MACOS / ETC
     * - DTO 계층에서는 String으로 받는다
     * - enum 변환은 Service 내부에서만 수행 (선택)
     */
    @NotBlank(message = "Device type cannot be blank")
    private String deviceType;

    @NullOrNotBlank(message = "Device notification token can be null but not blank")
    private String notificationToken;

    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, String deviceType, String notificationToken) {
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

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
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
