package com.stock.dashboard.backend.portfolio.dto;

import java.util.List;

public record PortfolioResponse(
        List<PositionResponse> positions,
        SummaryResponse summary,
         List<WarningResponse> warnings
) {}