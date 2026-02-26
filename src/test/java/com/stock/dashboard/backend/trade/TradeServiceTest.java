package com.stock.dashboard.backend.trade;

import com.stock.dashboard.backend.exception.BadRequestException;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.trade.dto.CreateTradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class TradeServiceTest {

    @Autowired TradeRepository tradeRepository;
    @Autowired UserRepository userRepository;

    TradeService tradeService;

    @BeforeEach
    void setUp() {
        // ✅ TradeService는 빈으로 띄우지 않고 테스트에서 직접 생성 (외부 의존성 차단)
        this.tradeService = new TradeService(tradeRepository);
    }

    @Test
    void sell_over_holding_should_throw_bad_request() {
        System.out.println("[TEST] sell_over_holding_should_throw_bad_request");

        // given: 저장된 유저 (id 필요)
        User user = userRepository.save(
                new User(
                        "test+" + System.nanoTime() + "@test.com",
                        "pw",
                        "tester",
                        20,
                        "010-0000-0000",
                        "local",
                        true // emailVerified
                )
        );

        // BUY 1주
        CreateTradeRequest buy = new CreateTradeRequest();
        buy.setSymbol("AAPL");
        buy.setSide(TradeSide.BUY);
        buy.setKind(OrderKind.MARKET);
        buy.setQuantity(1);
        buy.setPriceUsd(new BigDecimal("100.00"));
        tradeService.create(user, buy);

        // when: SELL 2주 (보유수량 초과)
        CreateTradeRequest sell = new CreateTradeRequest();
        sell.setSymbol("AAPL");
        sell.setSide(TradeSide.SELL);
        sell.setKind(OrderKind.MARKET);
        sell.setQuantity(2);
        sell.setPriceUsd(new BigDecimal("120.00"));

        // then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> tradeService.create(user, sell));

        assertTrue(ex.getMessage().contains("보유수량 초과 매도"));
    }

    @Test
    void sell_within_holding_should_succeed_and_reduce_holding() {
        System.out.println("[TEST] sell_within_holding_should_succeed_and_reduce_holding");

        // given
        User user = userRepository.save(
                new User(
                        "test+" + System.nanoTime() + "@test.com",
                        "pw",
                        "tester",
                        20,
                        "010-0000-0000",
                        "local",
                        true
                )
        );

        // BUY 2주
        CreateTradeRequest buy = new CreateTradeRequest();
        buy.setSymbol("AAPL");
        buy.setSide(TradeSide.BUY);
        buy.setKind(OrderKind.MARKET);
        buy.setQuantity(2);
        buy.setPriceUsd(new BigDecimal("100.00"));
        Long buyId = tradeService.create(user, buy);
        assertNotNull(buyId);

        // when: SELL 1주 (정상)
        CreateTradeRequest sell = new CreateTradeRequest();
        sell.setSymbol("AAPL");
        sell.setSide(TradeSide.SELL);
        sell.setKind(OrderKind.MARKET);
        sell.setQuantity(1);
        sell.setPriceUsd(new BigDecimal("120.00"));

        Long sellId = assertDoesNotThrow(() -> tradeService.create(user, sell));
        assertNotNull(sellId);

        // then: DB에 trade 2개 저장됐는지 확인
        assertEquals(2, tradeRepository.findByUserIdOrderByTradedAtDesc(user.getId()).size());

        // then: 보유수량이 1로 줄었는지 확인 (BUY 2 - SELL 1 = 1)
        Integer holdingQty = tradeRepository.getNetQuantity(user.getId(), "AAPL");
        assertEquals(1, holdingQty);
    }

    @Test
    void consecutive_sells_should_block_when_holding_runs_out() {
        System.out.println("[TEST] consecutive_sells_should_block_when_holding_runs_out");

        // given
        User user = userRepository.save(
                new User(
                        "test+" + System.nanoTime() + "@test.com",
                        "pw",
                        "tester",
                        20,
                        "010-0000-0000",
                        "local",
                        true
                )
        );

        // BUY 2주
        CreateTradeRequest buy = new CreateTradeRequest();
        buy.setSymbol("AAPL");
        buy.setSide(TradeSide.BUY);
        buy.setKind(OrderKind.MARKET);
        buy.setQuantity(2);
        buy.setPriceUsd(new BigDecimal("100.00"));
        tradeService.create(user, buy);

        // SELL 1주 (정상)
        CreateTradeRequest sell1 = new CreateTradeRequest();
        sell1.setSymbol("AAPL");
        sell1.setSide(TradeSide.SELL);
        sell1.setKind(OrderKind.MARKET);
        sell1.setQuantity(1);
        sell1.setPriceUsd(new BigDecimal("110.00"));
        tradeService.create(user, sell1);

        // 현재 보유수량: 1인지 확인 (안전망)
        Integer holdingAfterSell1 = tradeRepository.getNetQuantity(user.getId(), "AAPL");
        assertEquals(1, holdingAfterSell1);

        // when: SELL 2주 (이제 보유 1인데 2 팔려는 시도 → 실패해야 함)
        CreateTradeRequest sell2 = new CreateTradeRequest();
        sell2.setSymbol("AAPL");
        sell2.setSide(TradeSide.SELL);
        sell2.setKind(OrderKind.MARKET);
        sell2.setQuantity(2);
        sell2.setPriceUsd(new BigDecimal("120.00"));

        // then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> tradeService.create(user, sell2));

        assertTrue(ex.getMessage().contains("보유수량 초과 매도"));

        // 실패했으니 trade는 2개(BUY+SELL1)만 있어야 함
        assertEquals(2, tradeRepository.findByUserIdOrderByTradedAtDesc(user.getId()).size());

        // 최종 보유수량도 그대로 1 유지
        Integer finalHolding = tradeRepository.getNetQuantity(user.getId(), "AAPL");
        assertEquals(1, finalHolding);
    }
}