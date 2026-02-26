package com.stock.dashboard.backend.portfolio.dto;

import java.math.BigDecimal;

public record PositionResponse(
        String symbol,
        int quantity,
        BigDecimal avgBuyPriceUsd,
        BigDecimal currentPriceUsd,
        BigDecimal marketValueUsd,
        BigDecimal unrealizedPnlUsd,
        BigDecimal realizedPnlUsd,
        BigDecimal unrealizedReturnPct
) {}