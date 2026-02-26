package com.stock.dashboard.backend.portfolio;

import com.stock.dashboard.backend.market.bok.BokExchangeRateService;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import com.stock.dashboard.backend.trade.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.stock.dashboard.backend.portfolio.PortfolioWarningCodes.QUOTE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceUnitTest {

    @Mock TradeRepository tradeRepository;
    @Mock MarketRealtimePriceService marketRealtimePriceService;
    @Mock BokExchangeRateService bokExchangeRateService;

    @InjectMocks PortfolioService portfolioService;

    @Test
    void portfolio_should_return_summary_and_positions() {
        Long userId = 1L;

        // User 엔티티 생성자 복잡하면 null/더미로 최소만 채워도 됨 (Trade.of가 User를 필요로 하면 User 객체만 있으면 됨)
        User user = new User("p@test.com", "pw", "tester", 20, "010", "local", true);
        // 만약 getId()가 필요하면, Trade.of 대신 Trade.builder로 userId만 넣는 방식이 필요할 수 있음.
        // 일단 네 코드 스타일대로 Trade.of(user,...)가 된다고 가정.

        Trade buy = Trade.of(user, "AAPL", TradeSide.BUY, OrderKind.MARKET, 2,
                new BigDecimal("100.00"), null);
        // tradedAt 정렬/계산에 쓰면 세팅


        Trade sell = Trade.of(user, "AAPL", TradeSide.SELL, OrderKind.MARKET, 1,
                new BigDecimal("120.00"), null);


        when(tradeRepository.findByUser_IdOrderByTradedAtAsc(userId))
                .thenReturn(List.of(buy, sell));

        when(marketRealtimePriceService.getRealtimePrice("AAPL"))
                .thenReturn(MarketSummaryVO.builder().symbol("AAPL").price(110).build());

        when(bokExchangeRateService.getUsdKrwRate()).thenReturn(1300.0);

        var res = portfolioService.getPortfolio(userId);

        assertNotNull(res.summary());
        assertEquals(1, res.positions().size());
        assertTrue(res.warnings().isEmpty());
    }
    @Test
    void portfolio_returnPct_should_be_calculated_correctly() {
        Long userId = 1L;
        User user = new User("p@test.com", "pw", "tester", 20, "010", "local", true);

        Trade buy = Trade.of(user, "AAPL", TradeSide.BUY, OrderKind.MARKET, 2,
                new BigDecimal("100.00"), null);

        Trade sell = Trade.of(user, "AAPL", TradeSide.SELL, OrderKind.MARKET, 1,
                new BigDecimal("120.00"), null);

        when(tradeRepository.findByUser_IdOrderByTradedAtAsc(userId))
                .thenReturn(List.of(buy, sell));

        when(marketRealtimePriceService.getRealtimePrice("AAPL"))
                .thenReturn(MarketSummaryVO.builder().symbol("AAPL").price(110).build());

        when(bokExchangeRateService.getUsdKrwRate())
                .thenReturn(1300.0);

        var res = portfolioService.getPortfolio(userId);
        var summary = res.summary();

        assertNotNull(summary);

        // 🔹 총 실현 손익 = +20
        assertEquals(new BigDecimal("20.00"), summary.totalRealizedPnlUsd());

        // 🔹 총 미실현 손익 = +10
        assertEquals(new BigDecimal("10.00"), summary.totalUnrealizedPnlUsd());

        // 🔹 총 손익 = 30
        assertEquals(new BigDecimal("30.00"), summary.totalPnlUsd());

        // 🔹 총 원가(남은 1주 * 100)
        assertEquals(new BigDecimal("100.00"), summary.totalCostUsd());

        // 🔹 수익률 = 30%
        assertEquals(new BigDecimal("30.00"), summary.totalReturnPct());
    }
    @Test
    void portfolio_should_add_warning_when_quote_unavailable() {
        Long userId = 1L;
        User user = new User("p@test.com", "pw", "tester", 20, "010", "local", true);

        Trade buy = Trade.of(user, "AAPL", TradeSide.BUY, OrderKind.MARKET, 1,
                new BigDecimal("100.00"), null);

        when(tradeRepository.findByUser_IdOrderByTradedAtAsc(userId))
                .thenReturn(List.of(buy));

        // 시세 조회 실패 유도
        when(marketRealtimePriceService.getRealtimePrice("AAPL"))
                .thenThrow(new RuntimeException("quote api down"));

        when(bokExchangeRateService.getUsdKrwRate())
                .thenReturn(1300.0);

        var res = portfolioService.getPortfolio(userId);

        // 포지션 생성 안됨
        assertTrue(res.positions().isEmpty());

        // 경고 발생 확인
        assertFalse(res.warnings().isEmpty());

        var warning = res.warnings().get(0);
        assertEquals(QUOTE_UNAVAILABLE, warning.code());
        assertEquals("AAPL", warning.symbol());
    }
}