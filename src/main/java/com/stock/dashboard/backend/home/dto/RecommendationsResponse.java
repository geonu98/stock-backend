package com.stock.dashboard.backend.home.dto;

import java.util.List;

public record RecommendationsResponse(
        List<RecommendedItemResponse> items,
        Integer nextOffset
) {}
