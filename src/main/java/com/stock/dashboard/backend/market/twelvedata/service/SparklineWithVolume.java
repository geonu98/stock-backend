package com.stock.dashboard.backend.market.twelvedata.service;

import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import java.util.List;

public record SparklineWithVolume(
        List<SparklinePoint> sparkline,
        long volume
) {
    public static SparklineWithVolume empty() {
        return new SparklineWithVolume(List.of(), 0L);
    }
}
