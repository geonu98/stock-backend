package com.stock.dashboard.backend.market.twelvedata.service;

import com.stock.dashboard.backend.market.client.TwelveDataTimeSeriesClient;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SparklineService {

    private final TwelveDataTimeSeriesClient timeSeriesClient;

    /**
     * 스파크라인 + 최신 1개 volume 함께 반환
     *
     * - sparkline: outputsize=30
     * - volume: outputsize=1 (랭킹용 비용 절감)
     */
    public SparklineWithVolume getSparklineWithVolume(String symbol) {
        try {
            // 1) sparkline용 (30개)
            TwelveDataTimeSeriesResponse sparkRes = timeSeriesClient.fetchSparkline(symbol);
            List<SparklinePoint> sparkline = toSparkline(sparkRes);

            // 2) volume용 (최신 1개)
            long volume = getLatestVolume(symbol);

            return new SparklineWithVolume(sparkline, volume);
        } catch (Exception e) {
            log.warn("getSparklineWithVolume failed symbol={} msg={}", symbol, e.getMessage());
            return new SparklineWithVolume(List.of(), 0L);
        }
    }

    /**
     * 기존 호환용 (스파크라인만)
     */
    public List<SparklinePoint> getSparkline(String symbol) {
        return getSparklineWithVolume(symbol).sparkline();
    }

    /**
     * 랭킹용: 최신 1개 캔들에서 volume만 추출 (outputsize=1)
     */
    public long getLatestVolume(String symbol) {
        try {
            TwelveDataTimeSeriesResponse res = timeSeriesClient.fetchLatestForVolume(symbol);
            if (res == null || res.getValues() == null || res.getValues().isEmpty()) return 0L;

            TwelveDataTimeSeriesResponse.Value first = res.getValues().get(0);
            long volume = parseLongSafe(first.getVolume());

            // 필요하면 로그 (너무 많으면 debug로 낮춰)
            log.info("volume symbol={} volume={} close={}", symbol, volume, first.getClose());

            return volume;
        } catch (Exception e) {
            log.warn("getLatestVolume failed symbol={} msg={}", symbol, e.getMessage());
            return 0L;
        }
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

        List<Double> reversed = new java.util.ArrayList<>(closes);
        Collections.reverse(reversed);

        return IntStream.range(0, reversed.size())
                .mapToObj(i -> new SparklinePoint(i, reversed.get(i)))
                .toList();
    }

    private static Double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            // "12345" 뿐 아니라 "12345.0" 같은 케이스도 안전 처리
            return (long) Double.parseDouble(s);
        } catch (Exception e) {
            return 0L;
        }
    }
}
