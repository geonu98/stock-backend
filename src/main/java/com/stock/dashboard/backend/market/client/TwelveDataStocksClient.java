package com.stock.dashboard.backend.market.client;

import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataStocksResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class TwelveDataStocksClient {

    private final RestTemplate restTemplate;

    @Value("${twelvedata.base-url}")
    private String baseUrl;

    @Value("${twelvedata.api-key}")
    private String apiKey;

    public TwelveDataStocksResponse fetchNasdaqStocks() {
        String url = baseUrl + "/stocks"
                + "?exchange=NASDAQ"
                + "&apikey=" + apiKey;

        return restTemplate.getForObject(url, TwelveDataStocksResponse.class);
    }
}
