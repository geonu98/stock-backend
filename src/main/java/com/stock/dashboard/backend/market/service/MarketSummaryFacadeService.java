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

        // ✅ days는 "응답 slice용"
        // - CandleService는 내부적으로 MAX_DAYS를 1번만 호출/캐시
        // - 여기서는 days만큼 잘린 candles를 받는다
        List<DailyCandleDTO> candles = marketCandleService.getDailyCandles(symbol, days);

        return MarketSummaryResponse.builder()
                .quote(quote)
                .candles(candles)
                .build();
    }
}
