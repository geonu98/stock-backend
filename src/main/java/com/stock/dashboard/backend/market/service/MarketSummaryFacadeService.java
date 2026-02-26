package com.stock.dashboard.backend.market.service;

import com.stock.dashboard.backend.market.dto.DailyCandleDTO;
import com.stock.dashboard.backend.market.dto.MarketSummaryResponse;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketSummaryFacadeService {

    private final MarketRealtimePriceService marketRealtimePriceService; // 실시간 quote
    private final MarketCandleService marketCandleService;              // TwelveData (일봉)

    public MarketSummaryResponse getSummary(String symbol, int days) {
        MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(symbol);

        // ✅ days를 service로 넘겨서
        // - TwelveData outputsize를 days로 맞추고
        // - 캐시 키도 symbol:days로 정확히 먹게 함
        List<DailyCandleDTO> candles = marketCandleService.getDailyCandles(symbol, days);

        return MarketSummaryResponse.builder()
                .quote(quote)
                .candles(candles)
                .build();
    }
}
