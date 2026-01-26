package com.stock.dashboard.backend.home.dto;

import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import java.util.List;

public record RecommendedItemResponse(
        String symbol,
        Double price,
        Double changeRate,
        List<SparklinePoint> sparkline
) {}
