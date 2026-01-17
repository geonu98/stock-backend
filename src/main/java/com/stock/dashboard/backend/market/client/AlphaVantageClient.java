package com.stock.dashboard.backend.market.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class AlphaVantageClient {

    @Value("${alphavantage.base-url}")
    private String baseUrl;

    @Value("${alphavantage.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public String getGlobalQuoteRaw(String symbol) {
        String url = String.format(
                "%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                baseUrl, symbol, apiKey
        );
        return restTemplate.getForObject(url, String.class);
    }
}
