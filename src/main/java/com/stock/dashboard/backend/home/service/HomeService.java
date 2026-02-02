package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.home.dto.RecommendationsResponse;
import com.stock.dashboard.backend.home.dto.RecommendedItemResponse;
import com.stock.dashboard.backend.home.vo.HomeResponseVO;
import com.stock.dashboard.backend.home.vo.HomeSnapshot;
import com.stock.dashboard.backend.home.vo.HomeTickerVO;
import com.stock.dashboard.backend.home.vo.NewsItemVO;
import com.stock.dashboard.backend.market.client.FinnhubClient;
import com.stock.dashboard.backend.market.dto.FinnhubNewsItemDTO;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.market.twelvedata.service.SparklineService;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    private final HomeRecommendationService homeRecommendationService;
    private final HomeCacheStore homeCacheStore;

    @Value("${home.symbols:AAPL,TSLA,NVDA,AMZN}")
    private String symbolsCsv;

    @Value("${home.news-category:general}")
    private String newsCategory;

    @Value("${home.news-limit:10}")
    private int newsLimit;

    @Value("${home.recommend.page-size:10}")
    private int pageSize;

    // 1) 외부 API 호출은 여기서만 함 (스케줄러가 호출)
    public void refreshHomeCache() {
        HomeSnapshot fresh = buildSnapshot();
        homeCacheStore.set(fresh);

        log.info("Home cache refreshed. snapshotId={}, tickers={}, news={}, recoHome={}",
                fresh.getSnapshotId(),
                fresh.getTickers() == null ? 0 : fresh.getTickers().size(),
                fresh.getNews() == null ? 0 : fresh.getNews().size(),
                fresh.getRecommendationItems() == null ? 0 : fresh.getRecommendationItems().size()
        );
    }

    // 2) /api/home : 캐시 기반 반환 (추천은 홈용 5개만)
    public HomeResponseVO getHome() {
        HomeSnapshot snap = getOrBuildSnapshotSafe();

        // 홈 추천은 스냅샷에 "이미 5개만" 들어있다고 가정
        RecommendationsResponse recoPage0 = toRecoPage0FromHomeList(snap.getRecommendationItems());

        return HomeResponseVO.builder()
                .tickers(snap.getTickers())
                .news(snap.getNews())
                .recommendations(recoPage0)
                .build();
    }

    // 3) /api/home/recommendations : 더보기는 요청 시점에 계산(스냅샷 slice 금지)
    public RecommendationsResponse getRecommendationsFromCache(int offset) {
        // 더보기는 눌렀을 때만 호출되니까 "실시간 계산/스킵 방식"으로 가도 UX OK
        return homeRecommendationService.getRecommendations(offset);
    }

    private HomeSnapshot getOrBuildSnapshotSafe() {
        HomeSnapshot cached = homeCacheStore.get();
        if (cached != null) return cached;

        // 서버 첫 실행 직후 캐시가 비어있을 수 있으니 1회 빌드 시도
        try {
            HomeSnapshot fresh = buildSnapshot();
            homeCacheStore.set(fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("Home cache empty and snapshot build failed. returning empty snapshot.", e);
            return HomeSnapshot.builder()
                    .snapshotId("empty")
                    .generatedAt(Instant.EPOCH)
                    .tickers(List.of())
                    .news(List.of())
                    .recommendationItems(List.of())
                    .build();
        }
    }

    private HomeSnapshot buildSnapshot() {
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

        // 홈은 빠르게 "5개만" 스냅샷에 저장
        List<RecommendedItemResponse> recoHome = homeRecommendationService.getRecommendationsForHome();

        return HomeSnapshot.builder()
                .snapshotId(String.valueOf(System.currentTimeMillis()))
                .generatedAt(Instant.now())
                .tickers(tickers)
                .news(news)
                .recommendationItems(recoHome)
                .build();
    }

    private RecommendationsResponse toRecoPage0FromHomeList(List<RecommendedItemResponse> homeList) {
        List<RecommendedItemResponse> all = (homeList == null) ? List.of() : homeList;
        int end = Math.min(all.size(), pageSize);

        List<RecommendedItemResponse> items = all.subList(0, end);

        // 홈 화면에서도 "더보기" 버튼 보여주고 싶으면 nextOffset을 0이 아니라 end로 주면 됨
        // 근데 데이터 원천이 5개뿐이라 nextOffset은 UI용 “더보기 있음” 신호로만 쓰는게 맞음
        // 실제 더보기 API는 getRecommendationsFromCache(offset)에서 실시간으로 계산
        Integer nextOffset = end > 0 ? end : null;

        return new RecommendationsResponse(items, nextOffset);
    }

    private HomeTickerVO buildTickerSafe(String symbol) {
        try {
            MarketSummaryVO p = marketRealtimePriceService.getRealtimePrice(symbol);
            var sparklinePoints = sparklineService.getSparkline(symbol);
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
}
