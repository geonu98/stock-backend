package com.stock.dashboard.backend.market.twelvedata.service;

import com.stock.dashboard.backend.market.client.TwelveDataTimeSeriesClient;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataTimeSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SparklineService {

    private final TwelveDataTimeSeriesClient timeSeriesClient;

    public List<SparklinePoint> getSparkline(String symbol) {
        try {
            TwelveDataTimeSeriesResponse response = timeSeriesClient.fetchSparkline(symbol);

            if (response == null || response.getValues() == null) {
                return Collections.emptyList();
            }

            List<Double> closes = response.getValues().stream()
                    .map(v -> Double.parseDouble(v.getClose()))
                    .collect(Collectors.toList());

            Collections.reverse(closes);

            return IntStream.range(0, closes.size())
                    .mapToObj(i -> new SparklinePoint(i, closes.get(i)))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
