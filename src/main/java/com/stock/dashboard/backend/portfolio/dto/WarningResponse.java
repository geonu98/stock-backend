package com.stock.dashboard.backend.portfolio.dto;

public record WarningResponse(
        String code,     // 예: FX_RATE_UNAVAILABLE
        String symbol    // 종목 관련 경고일 경우만 사용 (없으면 null)
) {}