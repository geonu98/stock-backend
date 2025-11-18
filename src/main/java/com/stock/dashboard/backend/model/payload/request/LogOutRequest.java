package com.stock.dashboard.backend.model.payload.request;

import com.stock.dashboard.backend.model.payload.DeviceInfo; // 🛑 경로 추정 및 수정 (DeviceInfo 파일 위치에 따라 변경될 수 있음)
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.Valid; // 🛑 Jakarta EE로 변경
import jakarta.validation.constraints.NotNull; // 🛑 Jakarta EE로 변경

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogOutRequest {

    @Valid
    @NotNull(message = "장치정보가 없습니다.")
    private DeviceInfo deviceInfo;
}
