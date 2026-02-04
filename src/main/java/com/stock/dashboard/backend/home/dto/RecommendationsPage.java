package com.stock.dashboard.backend.home.dto;


import java.util.List;

public record RecommendationsPage(
        List<RecommendationItem> items,
        Integer offset,
        Integer size,
        Boolean hasNext,
        Long snapshotVersion
) {
    public static RecommendationsPage home(List<RecommendationItem> items, long version) {
        return new RecommendationsPage(items, 0, items.size(), items.size() > 0, version);
    }

    public static RecommendationsPage page(List<RecommendationItem> items, int offset, int size, boolean hasNext, long version) {
        return new RecommendationsPage(items, offset, size, hasNext, version);
    }
}
