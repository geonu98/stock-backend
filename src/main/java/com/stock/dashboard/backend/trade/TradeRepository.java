package com.stock.dashboard.backend.trade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByTradedAtDesc(Long userId);

    List<Trade> findByUserIdAndSymbolOrderByTradedAtAsc(Long userId, String symbol);
}
