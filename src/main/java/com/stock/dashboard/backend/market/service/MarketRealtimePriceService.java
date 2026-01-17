package com.stock.dashboard.backend.market.service;

import com.stock.dashboard.backend.market.client.FinnhubClient;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketRealtimePriceService {

    private final FinnhubClient finnhubClient;

    public MarketSummaryVO getRealtimePrice(String symbol) {
        String s = (symbol == null ? "" : symbol.trim().toUpperCase());
        if (s.isEmpty()) {
            throw new IllegalArgumentException("symbol은 필수입니다.");
        }

        Map<String, Object> raw = finnhubClient.getQuoteRaw(s);

        double price = toDouble(raw.get("c"));   // current
        double change = toDouble(raw.get("d"));  // change
        double changePercent = toDouble(raw.get("dp")); // change %
        double high = toDouble(raw.get("h"));
        double low = toDouble(raw.get("l"));
        double open = toDouble(raw.get("o"));
        double prevClose = toDouble(raw.get("pc"));

        // MarketSummaryVO 필드명에 맞게 아래 매핑을 맞춰줘야 함
        // (VO가 어떤 필드를 갖는지 너 코드에 맞춰 조정)
        return MarketSummaryVO.builder()
                .symbol(s)
                .price(price)
                .open(open)
                .high(high)
                .low(low)
                .previousClose(prevClose)
                .change(change)
                .changePercent(changePercent)
                .volume(0L) // Finnhub quote 기본 응답에 volume 없음
                .build();
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }
}
