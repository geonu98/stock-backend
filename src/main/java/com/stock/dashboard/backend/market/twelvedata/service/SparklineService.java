package com.stock.dashboard.backend.market.twelvedata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.backend.exception.TwelveDataRateLimitException;
import com.stock.dashboard.backend.market.cache.RedisStringCache;
import com.stock.dashboard.backend.market.client.TwelveDataTimeSeriesClient;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SparklineService {

    private final TwelveDataTimeSeriesClient timeSeriesClient;
    private final RedisStringCache cache;
    private final ObjectMapper objectMapper;

    @Value("${home.sparkline-days:30}")
    private int sparklineDays;

    @Value("${sparkline.cache-ttl-seconds:21600}") // 6h
    private long sparklineTtlSeconds;

    private static final TypeReference<List<SparklinePoint>> SPARKLINE_LIST =
            new TypeReference<>() {};

    public List<SparklinePoint> getSparklineOnly(String symbol) {
        String cacheKey = sparklineKey(symbol, sparklineDays);

        // 1) 캐시 히트
        List<SparklinePoint> cached = readCache(cacheKey);
        if (cached != null) return cached;

        // 2) stampede 방지 락
        String lockKey = cacheKey + ":lock";
        boolean locked = Boolean.TRUE.equals(cache.setIfAbsent(lockKey, "1", Duration.ofSeconds(10)));

        if (!locked) {
            sleep(80);
            cached = readCache(cacheKey);
            if (cached != null) return cached;

            log.warn("sparkline cache miss but locked by others symbol={}", symbol);
            return List.of();
        }

        try {
            TwelveDataTimeSeriesResponse res = timeSeriesClient.fetchSparkline(symbol);
            List<SparklinePoint> points = toSparkline(res);

            if (points.isEmpty()) {
                cache.set(cacheKey, "[]", Duration.ofSeconds(60));
                return List.of();
            }

            cache.set(cacheKey, objectMapper.writeValueAsString(points), Duration.ofSeconds(sparklineTtlSeconds));
            return points;

        } catch (TwelveDataRateLimitException e) {
            throw e;

        } catch (Exception e) {
            log.warn("getSparklineOnly failed symbol={} ex={} msg={}",
                    symbol, e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        } finally {
            cache.delete(lockKey);
        }

    }

    private List<SparklinePoint> readCache(String key) {
        try {
            String json = cache.get(key);
            if (json == null) return null;
            return objectMapper.readValue(json, SPARKLINE_LIST);
        } catch (Exception e) {
            log.warn("sparkline cache parse failed key={} ex={}", key, e.getClass().getSimpleName());
            cache.delete(key);
            return null;
        }
    }

    private String sparklineKey(String symbol, int days) {
        return "sparkline:" + symbol + ":" + days;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private List<SparklinePoint> toSparkline(TwelveDataTimeSeriesResponse response) {
        if (response == null || response.getValues() == null || response.getValues().isEmpty()) {
            return List.of();
        }

        List<Double> closes = response.getValues().stream()
                .map(v -> parseDoubleSafe(v.getClose()))
                .filter(Objects::nonNull)
                .toList();

        if (closes.size() < 2) return List.of();

        var reversed = new java.util.ArrayList<>(closes);
        java.util.Collections.reverse(reversed);

        return IntStream.range(0, reversed.size())
                .mapToObj(i -> new SparklinePoint(i, reversed.get(i)))
                .toList();
    }

    private static Double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }
}
