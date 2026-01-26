package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.home.vo.HomeResponseVO;
import com.stock.dashboard.backend.home.vo.HomeTickerVO;
import com.stock.dashboard.backend.home.vo.NewsItemVO;
import com.stock.dashboard.backend.market.client.FinnhubClient;


import com.stock.dashboard.backend.market.dto.FinnhubNewsItemDTO;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.market.twelvedata.service.SparklineService;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeService {

    private final MarketRealtimePriceService marketRealtimePriceService;
    private final SparklineService sparklineService; // TwelveData
    private final FinnhubClient finnhubClient;
    private final HomeRecommendationService homeRecommendationService;


    @Value("${home.symbols}")
    private String symbolsCsv;

    @Value("${home.sparkline-days:30}")
    private int sparklineDays;

    @Value("${home-news.category:general}")
    private String newsCategory;

    @Value("${home-news.limit:10}")
    private int newsLimit;

    public HomeResponseVO getHome() {
        List<String> symbols = Arrays.stream(symbolsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        List<HomeTickerVO> tickers = symbols.stream()
                .map(this::buildTickerSafe)
                .filter(Objects::nonNull)
                .toList();

        List<NewsItemVO> news = buildNewsSafe();
        // 추천 1페이지 같이 내려주기
        var recommendations = homeRecommendationService.getRecommendations(0);

        return HomeResponseVO.builder()
                .tickers(tickers)   // 에디터픽
                .recommendations(recommendations) // 추천
                .news(news)
                .build();
    }

    private HomeTickerVO buildTickerSafe(String symbol) {
        try {
            // 1️⃣ 실시간 시세 (Finnhub)
            MarketSummaryVO p = marketRealtimePriceService.getRealtimePrice(symbol);

            // 2️⃣ 스파크라인 (TwelveData)
            var sparklinePoints = sparklineService.getSparkline(symbol);

            List<Double> sparkline = sparklinePoints.stream()
                    .map(SparklinePoint::getClose)
                    .toList();


            return HomeTickerVO.builder()
                    .symbol(symbol)
                    .name(null)
                    .price(p.getPrice())
                    .change(p.getChange())
                    .changePercent(p.getChangePercent())
                    .sparkline(sparkline)
                    .build();
        } catch (Exception e) {
            log.warn("Home ticker build failed: symbol={}", symbol, e);
            return null;
        }
    }

    private List<NewsItemVO> buildNewsSafe() {
        try {
            List<FinnhubNewsItemDTO> items = finnhubClient.getMarketNews(newsCategory);

            return items.stream()
                    .sorted(Comparator.comparingLong(FinnhubNewsItemDTO::getDatetime).reversed())
                    .limit(newsLimit)
                    .map(n -> NewsItemVO.builder()
                            .headline(n.getHeadline())
                            .source(n.getSource())
                            .datetime(n.getDatetime() * 1000L)
                            .url(n.getUrl())
                            .summary(n.getSummary())
                            .image(n.getImage())
                            .build())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}



