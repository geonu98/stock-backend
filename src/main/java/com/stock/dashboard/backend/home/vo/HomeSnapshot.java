package com.stock.dashboard.backend.home.vo;

import com.stock.dashboard.backend.home.dto.RecommendedItemResponse;
import com.stock.dashboard.backend.home.vo.HomeTickerVO;
import com.stock.dashboard.backend.home.vo.NewsItemVO;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HomeSnapshot {
    String snapshotId;
    Instant generatedAt;

    List<HomeTickerVO> tickers;
    List<NewsItemVO> news;

    // 핵심: 추천은 전체를 스냅샷에 저장
    List<RecommendedItemResponse> recommendationItems;
}
