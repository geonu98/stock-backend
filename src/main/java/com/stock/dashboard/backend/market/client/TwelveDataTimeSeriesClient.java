package com.stock.dashboard.backend.market.client;

import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
@Slf4j

@Component
@RequiredArgsConstructor
public class TwelveDataTimeSeriesClient {

    private final RestTemplate restTemplate;

    @Value("${twelvedata.base-url}")
    private String baseUrl;

    @Value("${twelvedata.api-key}")
    private String apiKey;

    @Value("${twelvedata.interval:1day}")
    private String interval;

    @Value("${twelvedata.outputsize.sparkline:30}")
    private int sparklineOutputSize;

    @Value("${twelvedata.outputsize.volume:1}")
    private int volumeOutputSize;

    public TwelveDataTimeSeriesResponse fetchSparkline(String symbol) {
        return fetchTimeSeries(symbol, sparklineOutputSize);
    }

    public TwelveDataTimeSeriesResponse fetchLatestForVolume(String symbol) {
        return fetchTimeSeries(symbol, volumeOutputSize);
    }

    private TwelveDataTimeSeriesResponse fetchTimeSeries(String symbol, int outputSize) {
        String url = baseUrl + "/time_series"
                + "?symbol=" + symbol
                + "&interval=" + interval
                + "&outputsize=" + outputSize
                + "&apikey=" + apiKey;

        return restTemplate.getForObject(url, TwelveDataTimeSeriesResponse.class);
    }
}
