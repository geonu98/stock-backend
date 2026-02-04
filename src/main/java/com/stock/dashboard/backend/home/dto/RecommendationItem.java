package com.stock.dashboard.backend.home.dto;

public record RecommendationItem(
        String symbol,
        long volume
) {
    public static RecommendationItem of(String symbol, long volume) {
        String s = (symbol == null) ? "" : symbol.trim().toUpperCase();
        return new RecommendationItem(s, Math.max(0L, volume));
    }
}
