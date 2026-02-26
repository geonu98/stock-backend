package com.stock.dashboard.backend.portfolio.dto;

import java.math.BigDecimal;

public record SummaryResponse(
        BigDecimal totalMarketValueUsd,
        BigDecimal totalUnrealizedPnlUsd,
        BigDecimal totalRealizedPnlUsd,
        BigDecimal totalPnlUsd,

        BigDecimal totalCostUsd,
        BigDecimal totalReturnPct,

        BigDecimal usdKrwRate,
        BigDecimal totalMarketValueKrw,
        BigDecimal totalPnlKrw
) {}