package com.stock.dashboard.backend.market.bok;

public record FxRateResponse(
        String base,
        String quote,
        Double rate
) {}
