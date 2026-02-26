package com.stock.dashboard.backend.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByTradedAtDesc(Long userId);

    List<Trade> findByUserIdAndSymbolOrderByTradedAtAsc(Long userId, String symbol);

    List<Trade> findByUser_IdOrderByTradedAtAsc(Long userId);

    // ✅ [추가] 특정 유저+심볼의 현재 보유수량(= BUY 합 - SELL 합)
    // - SUM 결과는 null일 수 있어서 COALESCE로 0 처리
    // - BUY는 +quantity, SELL은 -quantity
    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN t.side = com.stock.dashboard.backend.trade.TradeSide.BUY THEN t.quantity
                ELSE -t.quantity
            END
        ), 0)
        FROM Trade t
        WHERE t.user.id = :userId
          AND t.symbol = :symbol
    """)
    Integer getNetQuantity(@Param("userId") Long userId, @Param("symbol") String symbol);

    List<Trade> findByUser_IdAndSymbolOrderByTradedAtDesc(Long userId,String symbol);
}