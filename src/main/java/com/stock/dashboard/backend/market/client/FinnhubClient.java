package com.stock.dashboard.backend.market.client;

import com.stock.dashboard.backend.market.dto.FinnhubNewsItemDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpHeaders;


import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FinnhubClient {

    private final RestTemplate restTemplate;

    @Value("${finnhub.base-url}")
    private String baseUrl;

    @Value("${finnhub.api-key}")
    private String apiKey;

    @SuppressWarnings("unchecked")
    public Map<String, Object> getQuoteRaw(String symbol) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/quote")
                .queryParam("symbol", symbol)
                .queryParam("token", apiKey)
                .toUriString();

        return restTemplate.getForObject(url, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getGeneralNewsRaw() {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/news")
                .queryParam("category", "general")
                .queryParam("token", apiKey)
                .toUriString();

        return restTemplate.getForObject(url, List.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCompanyNewsRaw(String symbol, String from, String to) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/company-news")
                .queryParam("symbol", symbol)
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("token", apiKey)
                .toUriString();

        return restTemplate.getForObject(url, List.class);
    }
    public List<FinnhubNewsItemDTO> getMarketNews(String category) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/news")
                .queryParam("category", category)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<FinnhubNewsItemDTO[]> res =
                restTemplate.exchange(url, HttpMethod.GET, entity, FinnhubNewsItemDTO[].class);

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new IllegalStateException("Finnhub market news fetch failed: " + category);
        }

        return Arrays.asList(res.getBody());
    }

}
