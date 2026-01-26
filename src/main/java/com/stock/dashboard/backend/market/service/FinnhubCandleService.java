package com.stock.dashboard.backend.market.service;

import com.stock.dashboard.backend.market.dto.FinnhubCandleResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class FinnhubCandleService {

    private final RestTemplate restTemplate;

    @Value("${finnhub.base-url}")
    private String finnhubBaseUrl;

    @Value("${finnhub.api-key}")
    private String finnhubApiKey;
    public List<Double> getDailySparklineCloses(String symbol, int days) {
        long to = Instant.now().getEpochSecond();
        long from = Instant.now().minus(120, ChronoUnit.DAYS).getEpochSecond();

        String url = UriComponentsBuilder
                .fromHttpUrl(finnhubBaseUrl)
                .path("/stock/candle")
                .queryParam("symbol", symbol.toUpperCase())
                .queryParam("resolution", "D")
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("token", finnhubApiKey) // 핵심
                .build()
                .toUriString();

        log.info("[Finnhub] request symbol={}, from={}, to={}, days={}", symbol, from, to, days);

        try {
            FinnhubCandleResponseDTO body =
                    restTemplate.getForObject(url, FinnhubCandleResponseDTO.class);

            if (body == null) {
                log.warn("[Finnhub] body is null: symbol={}", symbol);
                return List.of();
            }

            log.info("[Finnhub] response symbol={}, status={}, closesSize={}",
                    symbol,
                    body.getS(),
                    body.getC() == null ? -1 : body.getC().size()
            );

            if (!"ok".equalsIgnoreCase(body.getS())) return List.of();
            if (body.getC() == null || body.getC().isEmpty()) return List.of();

            List<Double> closes = body.getC();
            int fromIdx = Math.max(0, closes.size() - days);
            return closes.subList(fromIdx, closes.size());

        } catch (Exception e) {
            log.error("[Finnhub] candle fetch failed: symbol={}, url={}", symbol, url, e);
            return List.of();
        }
    }


}
