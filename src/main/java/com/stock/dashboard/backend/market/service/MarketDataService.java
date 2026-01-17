package com.stock.dashboard.backend.market.service;

import com.stock.dashboard.backend.market.client.AlphaVantageClient;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * 기존 MarketService 로직을 거의 그대로 가져오되, **“호출은 client로”**만 분리함.
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {

    private final AlphaVantageClient alphaVantageClient;

    public MarketSummaryVO getRealtimePrice(String symbol) {
        try {
            String response = alphaVantageClient.getGlobalQuoteRaw(symbol);

            JSONObject root = new JSONObject(response);

            if (!root.has("Global Quote")) {
                log.error("Global Quote 없음. symbol={}, response={}", symbol, response);
                return null;
            }

            JSONObject quote = root.getJSONObject("Global Quote");

            return MarketSummaryVO.builder()
                    .symbol(quote.optString("01. symbol"))
                    .open(quote.optDouble("02. open", 0.0))
                    .high(quote.optDouble("03. high", 0.0))
                    .low(quote.optDouble("04. low", 0.0))
                    .price(quote.optDouble("05. price", 0.0))
                    .volume(quote.optLong("06. volume", 0))
                    .previousClose(quote.optDouble("08. previous close", 0.0))
                    .change(quote.optDouble("09. change", 0.0))
                    .changePercent(parsePercent(quote.optString("10. change percent")))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double parsePercent(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
