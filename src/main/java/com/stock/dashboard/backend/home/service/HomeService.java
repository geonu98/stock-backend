package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.home.vo.HomeResponseVO;
import com.stock.dashboard.backend.home.vo.HomeTickerVO;
import com.stock.dashboard.backend.home.vo.NewsItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    public HomeResponseVO getHome() {

        // 1) 티커(미니 차트 영역에 들어갈 것) - 일단 더미
        List<HomeTickerVO> tickers = List.of(
                HomeTickerVO.builder()
                        .symbol("AAPL")
                        .name("애플")
                        .price(257.64)
                        .change(-2.29)
                        .changePercent(-0.88)
                        .sparkline(List.of(250.0, 252.3, 255.1, 253.7, 257.6))
                        .build(),
                HomeTickerVO.builder()
                        .symbol("MSFT")
                        .name("마이크로소프트")
                        .price(457.99)
                        .change(-1.39)
                        .changePercent(-0.30)
                        .sparkline(List.of(450.0, 451.2, 452.8, 456.0, 458.0))
                        .build()
        );

        // 2) 뉴스 - 일단 더미
        List<NewsItemVO> news = List.of(
                NewsItemVO.builder()
                        .headline("미 연준, 금리 동결 발표")
                        .source("경제")
                        .datetime(System.currentTimeMillis())
                        .url("https://example.com/news/1")
                        .summary("시장 예상대로 금리 동결이 발표됐습니다.")
                        .image(null)
                        .build(),
                NewsItemVO.builder()
                        .headline("테크주 강세, 나스닥 상승")
                        .source("시장")
                        .datetime(System.currentTimeMillis() - 3600_000)
                        .url("https://example.com/news/2")
                        .summary("주요 빅테크가 상승하며 지수도 함께 올랐습니다.")
                        .image(null)
                        .build()
        );

        return HomeResponseVO.builder()
                .tickers(tickers)
                .news(news)
                .build();
    }
}
