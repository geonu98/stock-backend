package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.home.dto.RecommendationsResponse;
import com.stock.dashboard.backend.home.dto.RecommendedItemResponse;
import com.stock.dashboard.backend.home.vo.HomeResponseVO;
import com.stock.dashboard.backend.home.vo.HomeSnapshot;
import com.stock.dashboard.backend.home.vo.HomeTickerVO;
import com.stock.dashboard.backend.home.vo.NewsItemVO;
import com.stock.dashboard.backend.home.vo.RecommendationStatus;
import com.stock.dashboard.backend.market.client.FinnhubClient;
import com.stock.dashboard.backend.market.dto.FinnhubNewsItemDTO;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.market.twelvedata.service.SparklineService;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeService {

    private final MarketRealtimePriceService marketRealtimePriceService;
    private final SparklineService sparklineService;
    private final FinnhubClient finnhubClient;

    private final HomeCacheStore homeCacheStore;
    private final RecommendationPoolService recommendationPoolService;

    @Value("${home.symbols:AAPL,TSLA,NVDA,AMZN}")
    private String symbolsCsv;

    @Value("${home.news-category:general}")
    private String newsCategory;

    @Value("${home.news-limit:10}")
    private int newsLimit;

    @Value("${home.recommend.page-size:10}")
    private int pageSize;

    @Value("${home.recommend.home-size:5}")
    private int homeSize;

    // 1) 외부 API 호출은 여기서만 함 (스케줄러가 호출)
    public void refreshHomeCache() {
        HomeSnapshot fresh = buildSnapshot();
        homeCacheStore.set(fresh);

        log.info("Home cache refreshed. snapshotId={}, tickers={}, news={}, recoHome={}, recoStatus={}, recoVer={}",
                fresh.getSnapshotId(),
                fresh.getTickers() == null ? 0 : fresh.getTickers().size(),
                fresh.getNews() == null ? 0 : fresh.getNews().size(),
                fresh.getRecommendationItems() == null ? 0 : fresh.getRecommendationItems().size(),
                fresh.getRecommendationStatus(),
                fresh.getRecommendationVersion()
        );
    }

    // 2) /api/home : 캐시 기반 반환 (추천은 홈용 5개)
    public HomeResponseVO getHome() {
        HomeSnapshot snap = getOrBuildSnapshotSafe();

        // 홈/더보기 일치용 version
        String version = snap.getRecommendationVersion();

        RecommendationsResponse recoPage0 = toRecoPage0FromHomeList(snap.getRecommendationItems());

        return HomeResponseVO.builder()
                .tickers(snap.getTickers())
                .news(snap.getNews())
                .recommendations(recoPage0)

                .recommendationVersion(version)
                .recommendationStatus(snap.getRecommendationStatus())
                .recommendationUpdatedAt(snap.getRecommendationUpdatedAt())

                .build();
    }

    private HomeSnapshot getOrBuildSnapshotSafe() {
        HomeSnapshot cached = homeCacheStore.get();
        if (cached != null) return cached;

        try {
            HomeSnapshot fresh = buildSnapshot();
            homeCacheStore.set(fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("Home cache empty and snapshot build failed. returning empty snapshot.", e);

            long nowMs = System.currentTimeMillis();
            String version = recommendationPoolService.currentVersion();

            return HomeSnapshot.builder()
                    .snapshotId("empty")
                    .generatedAt(Instant.EPOCH)
                    .tickers(List.of())
                    .news(List.of())
                    .recommendationItems(List.of())
                    .recommendationVersion(version)
                    .recommendationStatus(RecommendationStatus.BUILDING)
                    .recommendationUpdatedAt(nowMs)
                    .build();
        }
    }

    private HomeSnapshot buildSnapshot() {
        long nowMs = System.currentTimeMillis();

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

        // 홈/더보기 일치용 version을 "스냅샷에 고정"한다.
        String version = recommendationPoolService.currentVersion();

        // 풀에서 version 기준으로 홈 5개 추출
        List<RecommendedItemResponse> fromPool = recommendationPoolService.getRecommendationsForHome(version);
        List<RecommendedItemResponse> recoHome = (fromPool == null) ? List.of() : fromPool;

        RecommendationStatus status = (recoHome.size() >= homeSize)
                ? RecommendationStatus.READY
                : RecommendationStatus.BUILDING;

        if (status == RecommendationStatus.BUILDING) {
            // 풀 부족이면 홈 UX 깨지지 않게 고정 심볼로 부족분 채워서 5개 보장
            recoHome = fillHomeRecommendationsWithFixedSymbols(recoHome, symbols, homeSize);
        }

        return HomeSnapshot.builder()
                .snapshotId(String.valueOf(nowMs))
                .generatedAt(Instant.now())
                .tickers(tickers)
                .news(news)

                .recommendationItems(recoHome)
                .recommendationVersion(version)
                .recommendationStatus(status)
                .recommendationUpdatedAt(nowMs)

                .build();
    }

    private RecommendationsResponse toRecoPage0FromHomeList(List<RecommendedItemResponse> homeList) {
        List<RecommendedItemResponse> all = (homeList == null) ? List.of() : homeList;
        int end = Math.min(all.size(), pageSize);

        List<RecommendedItemResponse> items = all.subList(0, end);

        // 홈은 5개만이니까 nextOffset은 UI 힌트 용도
        Integer nextOffset = end > 0 ? end : null;

        return new RecommendationsResponse(items, nextOffset);
    }

    private HomeTickerVO buildTickerSafe(String symbol) {
        try {
            MarketSummaryVO p = marketRealtimePriceService.getRealtimePrice(symbol);
            List<SparklinePoint> sparklinePoints = sparklineService.getSparklineOnly(symbol);
            List<Double> sparkline = sparklinePoints.stream().map(SparklinePoint::getClose).toList();

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

    /**
     * 홈 추천 5개 보장용 fallback
     * - 풀에서 일부만 나왔을 때, home.symbols(고정 심볼)로 부족분을 채운다.
     * - 이미 있는 심볼은 중복 제거한다.
     */
    private List<RecommendedItemResponse> fillHomeRecommendationsWithFixedSymbols(
            List<RecommendedItemResponse> base,
            List<String> fixedSymbols,
            int targetSize
    ) {
        List<RecommendedItemResponse> out = new ArrayList<>();
        if (base != null) out.addAll(base);

        Set<String> used = new HashSet<>();
        for (RecommendedItemResponse r : out) {
            if (r != null && r.symbol() != null) {
                used.add(r.symbol().trim().toUpperCase());
            }
        }

        if (fixedSymbols == null || fixedSymbols.isEmpty()) {
            return out.size() > targetSize ? out.subList(0, targetSize) : out;
        }

        for (String s : fixedSymbols) {
            if (out.size() >= targetSize) break;
            if (s == null || s.isBlank()) continue;

            String sym = s.trim().toUpperCase();
            if (used.contains(sym)) continue;

            try {
                MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(sym);
                List<SparklinePoint> sparkline = sparklineService.getSparklineOnly(sym);
                if (sparkline == null || sparkline.isEmpty()) continue;

                out.add(new RecommendedItemResponse(
                        sym,
                        quote.getPrice(),
                        quote.getChangePercent(),
                        sparkline
                ));
                used.add(sym);
            } catch (Exception e) {
                log.debug("fill home reco skip symbol={} ex={}", sym, e.getClass().getSimpleName());
            }
        }

        return out.size() > targetSize ? out.subList(0, targetSize) : out;
    }
}
