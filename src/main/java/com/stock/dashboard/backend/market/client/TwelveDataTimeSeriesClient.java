package com.stock.dashboard.backend.market.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.backend.exception.TwelveDataRateLimitException;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwelveDataTimeSeriesClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${twelvedata.base-url:https://api.twelvedata.com}")
    private String baseUrl;

    @Value("${twelvedata.api-key:}")
    private String apiKey;

    @Value("${home.sparkline-days:30}")
    private int sparklineDays;

    public TwelveDataTimeSeriesResponse fetchSparkline(String symbol) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/time_series")
                .queryParam("symbol", symbol)
                .queryParam("interval", "1day")
                .queryParam("outputsize", sparklineDays)
                .queryParam("format", "JSON")
                .queryParam("apikey", apiKey)
                .build(true)
                .toUri();

        return fetchAsDto("fetchSparkline", symbol, uri);
    }

    public TwelveDataTimeSeriesResponse fetchLatestForVolume(String symbol) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/time_series")
                .queryParam("symbol", symbol)
                .queryParam("interval", "1day")
                .queryParam("outputsize", 1)
                .queryParam("format", "JSON")
                .queryParam("apikey", apiKey)
                .build(true)
                .toUri();

        return fetchAsDto("fetchLatestForVolume", symbol, uri);
    }

    private TwelveDataTimeSeriesResponse fetchAsDto(String call, String symbol, URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes("application/json"));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String body = null;
        HttpStatusCode status = null;

        try {
            ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            status = resp.getStatusCode();
            body = resp.getBody();

            if (shouldLogSuspicious(status, body)) {
                log.warn("twelvedata {} suspicious symbol={} status={} bodyPrefix={}",
                        call, symbol, status.value(), prefix(body, 400));
            }

            if (!StringUtils.hasText(body)) {
                return null;
            }

            if (isRateLimitBody(body)) {
                throw new TwelveDataRateLimitException(extractRateLimitMessage(body));
            }

            return objectMapper.readValue(body, TwelveDataTimeSeriesResponse.class);

        } catch (TwelveDataRateLimitException e) {
            throw e;

        } catch (RestClientResponseException e) {
            String respBody = safeBody(e.getResponseBodyAsString());
            log.warn("twelvedata {} httpError symbol={} status={} bodyPrefix={}",
                    call, symbol, e.getRawStatusCode(), prefix(respBody, 400));

            if (isRateLimitBody(respBody)) {
                throw new TwelveDataRateLimitException(extractRateLimitMessage(respBody));
            }

            return null;

        } catch (Exception e) {
            log.warn("twelvedata {} failed symbol={} ex={} msg={} uri={}",
                    call, symbol, e.getClass().getSimpleName(), e.getMessage(), uri);
            if (StringUtils.hasText(body)) {
                log.warn("twelvedata {} parseFailRaw symbol={} status={} bodyPrefix={}",
                        call, symbol, status == null ? "null" : status.value(), prefix(body, 400));
            }
            return null;
        }
    }

    private boolean isRateLimitBody(String body) {
        if (!StringUtils.hasText(body)) return false;
        return body.contains("\"code\":429")
                || body.contains("\"code\": \"429\"")
                || body.contains("run out of API credits")
                || body.toLowerCase().contains("api credits")
                || body.toLowerCase().contains("rate limit");
    }

    private String extractRateLimitMessage(String body) {
        String p = prefix(body, 300);
        return "TWELVEDATA_RATE_LIMIT code=429 msg=" + p;
    }

    private boolean shouldLogSuspicious(HttpStatusCode status, String body) {
        if (status == null || !status.is2xxSuccessful()) return true;
        if (!StringUtils.hasText(body)) return true;

        if (isRateLimitBody(body)) return false;

        String b = body;
        if (b.contains("\"status\"") && b.contains("error")) return true;
        if (b.contains("\"code\"") && b.contains("\"message\"")) return true;
        if (!b.contains("\"values\"")) return true;
        if (b.contains("\"values\":[]")) return true;

        return false;
    }

    private String prefix(String s, int max) {
        if (!StringUtils.hasText(s)) return "null";
        String trimmed = s.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max) + "...";
    }

    private String safeBody(String s) {
        if (s == null) return null;
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }


    public TwelveDataTimeSeriesResponse fetchDailyCandles(String symbol, int days) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/time_series")
                .queryParam("symbol", symbol)
                .queryParam("interval", "1day")
                .queryParam("outputsize", days)
                .queryParam("format", "JSON")
                .queryParam("apikey", apiKey)
                .build(true)
                .toUri();

        return fetchAsDto("fetchDailyCandles", symbol, uri);
    }
}
