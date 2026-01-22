package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.market.bok.BokExchangeRateService;
import com.stock.dashboard.backend.market.bok.FxRateResponse;
import com.stock.dashboard.backend.market.dto.DailyCandleDTO;
import com.stock.dashboard.backend.market.dto.MarketSummaryResponse;
import com.stock.dashboard.backend.market.service.MarketCandleService;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.market.service.MarketSummaryFacadeService;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketCandleService marketCandleService;
    private final MarketRealtimePriceService marketRealtimePriceService;
    private  final BokExchangeRateService bokExchangeRateService;
    private final MarketSummaryFacadeService marketSummaryFacadeService;

    // 한국은행 Open API 키
    @Value("${bok.api-key}")
    private String bokApiKey;

    // 간단 호출용 (리팩토링 안 함)
    private final RestClient restClient = RestClient.create();

    /**
     * ✅ 개별 종목 실시간 가격
     */
    @GetMapping("/price")
    public MarketSummaryVO getPrice(@RequestParam String symbol) {
        return marketRealtimePriceService.getRealtimePrice(symbol);
    }

    /**
     * ✅ 개별 종목 일봉 캔들
     */
    @GetMapping("/candles/daily")
    public List<DailyCandleDTO> getDailyCandles(
            @RequestParam String symbol
    ) {
        return marketCandleService.getDailyCandles(symbol);
    }

    /**
     * ✅ USD/KRW 환율 (한국은행 Open API)
     * - 일별 매매기준율
     * - 홈 "시장요약"용
     * - 실패 시 null 반환
     */
    /**
     * ✅ 원/달러 환율 (한국은행 ECOS)
     */

    @GetMapping("/fx/usd-krw")
    public FxRateResponse getUsdKrw() {
        return new FxRateResponse("USD", "KRW", bokExchangeRateService.getUsdKrwRate());
    }



    @GetMapping("/summary")
    public ResponseEntity<MarketSummaryResponse> getSummary(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "90") int days
    ) {
        return ResponseEntity.ok(marketSummaryFacadeService.getSummary(symbol, days));
    }
}
