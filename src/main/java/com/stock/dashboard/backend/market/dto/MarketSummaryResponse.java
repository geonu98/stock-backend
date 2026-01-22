package com.stock.dashboard.backend.market.dto;

import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import java.util.List;
import lombok.Builder;

@Builder
public record MarketSummaryResponse(
        MarketSummaryVO quote,
        List<DailyCandleDTO> candles
) {}
