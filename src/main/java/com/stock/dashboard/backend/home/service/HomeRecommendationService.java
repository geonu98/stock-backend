package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.home.dto.RecommendedItemResponse;
import com.stock.dashboard.backend.home.dto.RecommendationsResponse;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataStockItem;
import com.stock.dashboard.backend.market.twelvedata.service.SparklineService;
import com.stock.dashboard.backend.market.twelvedata.service.StockCatalogService;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeRecommendationService {

    private final StockCatalogService stockCatalogService;
    private final SparklineService sparklineService;
    private final MarketRealtimePriceService marketRealtimePriceService;

    // 홈에 보여줄 추천 개수 (스냅샷)
    @Value("${home.recommend.home-size:5}")
    private int homeSize;

    // 더보기용 페이지 크기
    @Value("${home.recommend.page-size:10}")
    private int pageSize;

    // 후보 풀 크기 (랜덤/샘플링 기반)
    @Value("${home.recommend.candidate-pool:120}")
    private int candidatePool;

    // 홈 추천 생성 시간 제한
    @Value("${home.recommend.deadline-ms:2500}")
    private long deadlineMs;

    // 홈 추천 생성 시 최대 시도 횟수(유효 심볼 기준)
    @Value("${home.recommend.max-attempts:40}")
    private int maxAttempts;

    /**
     * 더보기(레거시): offset 기반 즉시 계산
     * - "나오는대로" 채우는 방식 (실패하면 스킵하고 다음 심볼 시도)
     */
    public RecommendationsResponse getRecommendations(int offset) {
        List<TwelveDataStockItem> pool = stockCatalogService.getCandidatePool(candidatePool);
        if (pool == null || pool.isEmpty()) {
            return new RecommendationsResponse(List.of(), null);
        }

        int start = Math.max(0, offset);

        List<RecommendedItemResponse> items = new ArrayList<>();
        int i = start;

        // 페이지를 "정확히 pageSize"만큼 채우도록 시도하되, 실패하면 다음으로 넘어감
        while (i < pool.size() && items.size() < pageSize) {
            String symbol = pool.get(i).getSymbol();
            i++;

            if (!isSafeCommonStockSymbol(symbol)) continue;

            try {
                MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(symbol);
                var sparkline = sparklineService.getSparkline(symbol);

                if (sparkline == null || sparkline.isEmpty()) continue;

                items.add(new RecommendedItemResponse(
                        symbol,
                        quote.getPrice(),
                        quote.getChangePercent(),
                        sparkline
                ));
            } catch (Exception e) {
                // 더보기는 실패해도 그냥 스킵 (필요하면 debug로)
                log.debug("more skip symbol={} msg={}", symbol, e.getMessage());
            }
        }

        Integer nextOffset = (i < pool.size()) ? i : null;
        return new RecommendationsResponse(items, nextOffset);
    }

    /**
     * 홈 스냅샷용 추천 (빠르게 5개만)
     * - deadline / attempts 로 부팅/스케줄러 지연 방지
     */
    public List<RecommendedItemResponse> getRecommendationsForHome() {
        List<TwelveDataStockItem> pool = stockCatalogService.getCandidatePool(candidatePool);
        if (pool == null || pool.isEmpty()) return List.of();

        long deadline = System.currentTimeMillis() + deadlineMs;

        List<RecommendedItemResponse> items = new ArrayList<>();
        int attempts = 0;

        for (TwelveDataStockItem it : pool) {
            if (items.size() >= homeSize) break;
            if (System.currentTimeMillis() > deadline) break;
            if (attempts >= maxAttempts) break;

            String symbol = it.getSymbol();
            if (!isSafeCommonStockSymbol(symbol)) continue;

            attempts++;

            try {
                MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(symbol);
                var sparkline = sparklineService.getSparkline(symbol);

                if (sparkline == null || sparkline.isEmpty()) continue;

                items.add(new RecommendedItemResponse(
                        symbol,
                        quote.getPrice(),
                        quote.getChangePercent(),
                        sparkline
                ));
            } catch (Exception e) {
                // 홈은 원인 로그 남기는게 중요 (왜 0인지 바로 보이게)
                log.info("home skip symbol={} msg={}", symbol, e.getMessage());
            }
        }

        log.info("home reco done size={} attempts={} deadlineMs={}", items.size(), attempts, deadlineMs);
        return items;
    }

    private boolean isSafeCommonStockSymbol(String symbol) {
        if (symbol == null) return false;
        return symbol.matches("^[A-Z]{1,5}$");
    }
}
