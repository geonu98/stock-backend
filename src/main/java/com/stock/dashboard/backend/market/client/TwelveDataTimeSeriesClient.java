package com.stock.dashboard.backend.market.client;

import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class TwelveDataTimeSeriesClient {

    private final RestTemplate restTemplate;

    @Value("${twelvedata.base-url}")
    private String baseUrl;

    @Value("${twelvedata.api-key}")
    private String apiKey;

    @Value("${twelvedata.interval}")
    private String interval;

    @Value("${twelvedata.outputsize}")
    private int outputSize;

    public TwelveDataTimeSeriesResponse fetchSparkline(String symbol) {
        String url = baseUrl + "/time_series"
                + "?symbol=" + symbol
                + "&interval=" + interval
                + "&outputsize=" + outputSize
                + "&apikey=" + apiKey;

        return restTemplate.getForObject(url, TwelveDataTimeSeriesResponse.class);
    }
}
