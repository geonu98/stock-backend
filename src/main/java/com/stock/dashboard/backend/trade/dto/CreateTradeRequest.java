package com.stock.dashboard.backend.trade.dto;

import com.stock.dashboard.backend.trade.OrderKind;
import com.stock.dashboard.backend.trade.TradeSide;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTradeRequest {
    private String symbol;
    private TradeSide side;      // BUY/SELL
    private OrderKind kind;      // MARKET/LIMIT
    private Integer quantity;    // 1 이상
    private BigDecimal priceUsd; // 체결가 스냅샷(필수)
    private BigDecimal usdKrwRate; // 선택
}
