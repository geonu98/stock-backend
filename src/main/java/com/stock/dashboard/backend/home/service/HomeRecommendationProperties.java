package com.stock.dashboard.backend.home.service;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "home.recommend")
public class HomeRecommendationProperties {

    private List<String> universe = new ArrayList<>();

    private int poolSize = 8;
    private int cacheTtlSeconds = 60;

    private int homeSize = 8;
    private int pageSize = 20;

    private int candidatePool = 60;
    private long deadlineMs = 1200;
    private int maxAttempts = 2;

    private int volumeTop = 60;

    private int fallbackPoolMult = 2;

    private List<String> fallbackSymbols =
            List.of("AAPL","MSFT","NVDA","AMZN","TSLA","GOOGL","META","SPY");
}
