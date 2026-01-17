package com.stock.dashboard.backend.home.vo;

import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HomeResponseVO {

    // 상단 미니 지수/티커
    private List<MarketSummaryVO> indices;

    // 오늘 인기 종목
    private List<MarketSummaryVO> popularStocks;

    // 뉴스
    private List<NewsItemVO> news;


    private List<HomeTickerVO> tickers;


     private Double usdKrw;
}
