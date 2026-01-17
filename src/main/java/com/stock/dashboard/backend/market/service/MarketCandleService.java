package com.stock.dashboard.backend.market.service;

import com.stock.dashboard.backend.market.dto.DailyCandleDTO;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MarketCandleService {

    @Value("${alphavantage.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<DailyCandleDTO> getDailyCandles(String symbol) {

        String url = String.format(
                "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                symbol.toUpperCase(), apiKey
        );

        String response = restTemplate.getForObject(url, String.class);
        JSONObject root = new JSONObject(response);

        if (!root.has("Time Series (Daily)")) {
            log.error("일봉 데이터 없음: {}", response);
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
                    .volume(d.optLong("6. volume"))
                    .build());
        }

        // 최신 → 과거 순이라서 차트용으로 정렬
        candles.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        log.info("AlphaVantage raw response: {}", response);
        return candles;
    }
}
