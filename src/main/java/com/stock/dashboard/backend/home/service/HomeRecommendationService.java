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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeRecommendationService {

    private final StockCatalogService stockCatalogService;
    private final SparklineService sparklineService;
    private final MarketRealtimePriceService marketRealtimePriceService;

    @Value("${home.recommend.page-size:10}")
    private int pageSize;

    @Value("${home.recommend.pool-size:50}")
    private int poolSize;

    public RecommendationsResponse getRecommendations(int offset) {
        List<TwelveDataStockItem> pool = stockCatalogService.getCandidatePool(poolSize);
        if (pool == null || pool.isEmpty()) {
            return new RecommendationsResponse(List.of(), null);
        }

        int start = Math.max(0, offset);
        int end = Math.min(pool.size(), start + pageSize);

        List<RecommendedItemResponse> items = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String symbol = pool.get(i).getSymbol();

            // 1️⃣ 실시간 시세 (Finnhub)
            MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(symbol);

            Double price = quote.getPrice();                 // 현재가
            Double changeRate = quote.getChangePercent();    //전일 대비 %

            //  스파크라인 (TwelveData close 30포인트)
            var sparkline = sparklineService.getSparkline(symbol);

            items.add(new RecommendedItemResponse(
                    symbol,
                    price,
                    changeRate,
                    sparkline
            ));
        }

        Integer nextOffset = (end < pool.size()) ? end : null;
        return new RecommendationsResponse(items, nextOffset);
    }
}
