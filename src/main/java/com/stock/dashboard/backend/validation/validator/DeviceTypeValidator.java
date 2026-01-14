package com.stock.dashboard.backend.validation.validator;

import com.stock.dashboard.backend.exception.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.Set;

public final class DeviceTypeValidator {

    private DeviceTypeValidator() {}

    // 정책 정의를 여기서 끝냄
    private static final String DEFAULT_DEVICE_TYPE = "WEB";

    private static final Set<String> ALLOWED_DEVICE_TYPES = Set.of(
            "WEB",
            "ANDROID",
            "IOS",
            "WINDOWS",
            "MACOS"
    );

    /**
     * raw deviceType → 표준화된 값 반환
     * - null / blank → WEB
     * - 허용되지 않은 값 → 400
     */
    public static String normalizeOrThrow(String rawDeviceType) {
        if (!StringUtils.hasText(rawDeviceType)) {
            return DEFAULT_DEVICE_TYPE;
        }

        String normalized = rawDeviceType.trim().toUpperCase();

        if (!ALLOWED_DEVICE_TYPES.contains(normalized)) {
            throw new BadRequestException(
                    "Invalid deviceType. Allowed values: " + ALLOWED_DEVICE_TYPES
            );
        }
        return normalized;
    }
}
