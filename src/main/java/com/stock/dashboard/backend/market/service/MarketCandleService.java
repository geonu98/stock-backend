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

    // ✅ 외부 호출은 딱 이 만큼만(7/30/90 버튼용이면 90 추천)
    private static final int MAX_DAYS = 90;

    // ✅ 캐시는 넉넉히 (원하는대로 조절)
    private static final Duration TTL = Duration.ofHours(12);

    private String key(String symbol) {
        return KEY_PREFIX + symbol.toUpperCase(); // ✅ days 제거
    }

    public List<DailyCandleDTO> getDailyCandles(String symbol, int days) {
        days = clamp(days, 1, MAX_DAYS);
        String k = key(symbol);

        // ✅ 1) 캐시 HIT: 전체(MAX_DAYS) 데이터 로드 후 slice
        String cached = cache.get(k);
        if (cached != null && !cached.isBlank()) {
            try {
                List<DailyCandleDTO> all = om.readValue(cached, new TypeReference<List<DailyCandleDTO>>() {});
                return sliceTail(all, days);
            } catch (Exception e) {
                cache.delete(k); // 캐시 포맷 꼬이면 삭제 후 재조회
            }
        }

        // ✅ 2) 캐시 MISS: TwelveData를 MAX_DAYS로 "한 번만" 호출
        TwelveDataTimeSeriesResponse resp = timeSeriesClient.fetchDailyCandles(symbol, MAX_DAYS);
        if (resp == null || resp.getValues() == null || resp.getValues().isEmpty()) {
            return List.of();
        }

        // TwelveData values: 최신→과거로 오는 경우가 많음 → date 오름차순 정렬
        List<DailyCandleDTO> all = resp.getValues().stream()
                .map(v -> DailyCandleDTO.builder()
                        .date(v.getDatetime())
                        .open(d(v.getOpen()))
                        .high(d(v.getHigh()))
                        .low(d(v.getLow()))
                        .close(d(v.getClose()))
                        .volume(l(v.getVolume()))
                        .build())
                .sorted(Comparator.comparing(DailyCandleDTO::getDate))
                .toList();

        // ✅ 3) 캐시에 MAX_DAYS 전체를 저장
        try {
            cache.set(k, om.writeValueAsString(all), TTL);
        } catch (Exception ignore) {}

        // ✅ 4) 요청 days만큼만 잘라서 반환
        return sliceTail(all, days);
    }

    private List<DailyCandleDTO> sliceTail(List<DailyCandleDTO> all, int days) {
        if (all == null || all.isEmpty()) return List.of();
        if (days <= 0) return all;
        int size = all.size();
        if (size <= days) return all;
        return all.subList(size - days, size);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private double d(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return 0d;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0d; }
    }

    private long l(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return 0L;
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }
}