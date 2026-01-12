package com.stock.dashboard.backend.service.email.dto;

import com.stock.dashboard.backend.model.payload.DeviceInfo;

public record VerifyCodePayload(
        Long userId,
        DeviceInfo deviceInfo
) {}
