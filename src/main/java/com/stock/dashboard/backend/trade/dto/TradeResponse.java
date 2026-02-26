package com.stock.dashboard.backend.trade.dto;

import com.stock.dashboard.backend.trade.OrderKind;
import com.stock.dashboard.backend.trade.Trade;
import com.stock.dashboard.backend.trade.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        String symbol,
        TradeSide side,
        OrderKind kind,
        Integer quantity,
        BigDecimal priceUsd,
        BigDecimal usdKrwRate,
        LocalDateTime tradedAt
) {
    public static TradeResponse from(Trade t) {
        return new TradeResponse(
                t.getId(),
                t.getSymbol(),
                t.getSide(),
                t.getKind(),
                t.getQuantity(),
                t.getPriceUsd(),
                t.getUsdKrwRate(),
                t.getTradedAt()
        );
    }
}