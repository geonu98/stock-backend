package com.stock.dashboard.backend.home.vo;

import com.stock.dashboard.backend.home.dto.RecommendationsResponse;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class HomeResponseVO {

    // 에디터픽 (home.symbols)
    private List<HomeTickerVO> tickers;

    // 추천 종목 (HomeRecommendationService)
    private RecommendationsResponse recommendations;

    // 뉴스
    private List<NewsItemVO> news;

    private Double usdKrw;


    private RecommendationStatus recommendationStatus;
    private Long recommendationUpdatedAt;

    private String recommendationVersion;
}
