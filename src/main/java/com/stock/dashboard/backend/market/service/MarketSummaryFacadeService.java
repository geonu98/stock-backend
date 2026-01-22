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

    private final MarketRealtimePriceService marketRealtimePriceService; // Finnhub (실시간 quote)
    private final MarketCandleService marketCandleService;               // AlphaVantage (일봉)

    public MarketSummaryResponse getSummary(String symbol, int days) {
        MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(symbol);
        List<DailyCandleDTO> candles = marketCandleService.getDailyCandles(symbol);

        // days만큼만 잘라서 내려주기(선택, 프론트 slice 안 해도 됨)
        if (days > 0 && candles.size() > days) {
            candles = candles.subList(candles.size() - days, candles.size());
        }

        return MarketSummaryResponse.builder()
                .quote(quote)
                .candles(candles)
                .build();
    }
}
