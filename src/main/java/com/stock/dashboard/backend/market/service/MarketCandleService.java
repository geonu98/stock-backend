package com.stock.dashboard.backend.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.backend.market.dto.DailyCandleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketCandleService {

    @Value("${alphavantage.api-key}")
    private String apiKey;

    private final RedisTemplate<String, Object> redisTemplate;

//    private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate restTemplate;

    private static final long CANDLE_TTL_MIN = 60; // 60분 캐시
    private static final String KEY_PREFIX = "market:candles:";

    private final ObjectMapper om = new ObjectMapper();

    private String key(String symbol) {
        return KEY_PREFIX + symbol.toUpperCase();
    }

    public List<DailyCandleDTO> getDailyCandles(String symbol) {

        //   Redis 캐시 조회 (JSON String)
        Object cached = redisTemplate.opsForValue().get(key(symbol));
        if (cached instanceof String cachedJson && !cachedJson.isBlank()) {
            try {
                return om.readValue(
                        cachedJson,
                        new TypeReference<List<DailyCandleDTO>>() {}
                );
            } catch (Exception e) {
                // 캐시 포맷이 꼬였으면 지우고 새로 채움
                redisTemplate.delete(key(symbol));
            }
        }

        //   캐시 MISS → AlphaVantage 호출
        String url = String.format(
                "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                symbol.toUpperCase(), apiKey
        );

        String response = restTemplate.getForObject(url, String.class);
        JSONObject root = new JSONObject(response);

        if (!root.has("Time Series (Daily)")) {
            log.error("일봉 데이터 없음: {}", response);

            //  기존 캐시라도 다시 시도
            Object fallback = redisTemplate.opsForValue().get(key(symbol));
            if (fallback instanceof String cachedJson && !cachedJson.isBlank()) {
                try {
                    return om.readValue(
                            cachedJson,
                            new TypeReference<List<DailyCandleDTO>>() {}
                    );
                } catch (Exception ignore) {}
            }

            return List.of();
        }
        JSONObject timeSeries = root.getJSONObject("Time Series (Daily)");
        List<DailyCandleDTO> candles = new ArrayList<>();

        for (String date : timeSeries.keySet()) {
            JSONObject d = timeSeries.getJSONObject(date);

            candles.add(DailyCandleDTO.builder()
                    .date(date)
                    .open(d.optDouble("1. open"))
                    .high(d.optDouble("2. high"))
                    .low(d.optDouble("3. low"))
                    .close(d.optDouble("4. close"))
                    .volume(d.optLong("5. volume"))
                    .build());
        }

        // 최신 → 과거 순이라서 차트용으로 정렬
        candles.sort((a, b) -> a.getDate().compareTo(b.getDate()));

        //   Redis 저장 (JSON String)
        try {
            String json = om.writeValueAsString(candles);
            redisTemplate.opsForValue().set(key(symbol), json, CANDLE_TTL_MIN, TimeUnit.MINUTES);
        } catch (Exception ignore) {}

        return candles;
    }
}

