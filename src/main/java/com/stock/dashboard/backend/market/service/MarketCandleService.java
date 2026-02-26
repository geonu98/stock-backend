package com.stock.dashboard.backend.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.backend.market.cache.RedisStringCache;
import com.stock.dashboard.backend.market.client.TwelveDataTimeSeriesClient;
import com.stock.dashboard.backend.market.dto.DailyCandleDTO;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketCandleService {

    private final TwelveDataTimeSeriesClient timeSeriesClient;
    private final RedisStringCache cache;
    private final ObjectMapper om;

    private static final String KEY_PREFIX = "market:candles:1day:";
    private static final Duration TTL = Duration.ofHours(12);

    private String key(String symbol, int days) {
        return KEY_PREFIX + symbol.toUpperCase() + ":" + days;
    }

    // ✅ 안전 파싱(값 비었거나 "null"일 때 500 방지)
    private double d(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return 0d;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0d; }
    }

    private long l(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return 0L;
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    public List<DailyCandleDTO> getDailyCandles(String symbol, int days) {
        days = Math.max(1, Math.min(days, 365));
        String k = key(symbol, days);

        // ✅ cache HIT
        String cached = cache.get(k);
        if (cached != null && !cached.isBlank()) {
            try {
                return om.readValue(cached, new TypeReference<List<DailyCandleDTO>>() {});
            } catch (Exception e) {
                cache.delete(k);
            }
        }

        // ✅ TwelveData 호출
        TwelveDataTimeSeriesResponse resp = timeSeriesClient.fetchDailyCandles(symbol, days);
        if (resp == null || resp.getValues() == null || resp.getValues().isEmpty()) {
            return List.of();
        }

        // ✅ 날짜 오름차순 정렬 + 안전 파싱 적용
        List<DailyCandleDTO> candles = resp.getValues().stream()
                .map(v -> DailyCandleDTO.builder()
                        .date(v.getDatetime()) // 보통 "YYYY-MM-DD"
                        .open(d(v.getOpen()))
                        .high(d(v.getHigh()))
                        .low(d(v.getLow()))
                        .close(d(v.getClose()))
                        .volume(l(v.getVolume()))
                        .build())
                .sorted(Comparator.comparing(DailyCandleDTO::getDate))
                .toList();

        // ✅ cache SET
        try {
            cache.set(k, om.writeValueAsString(candles), TTL);
        } catch (Exception ignore) {}

        return candles;
    }
}