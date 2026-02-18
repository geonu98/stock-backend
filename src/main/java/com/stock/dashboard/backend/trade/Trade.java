package com.stock.dashboard.backend.trade;

import com.stock.dashboard.backend.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "TRADES",
        indexes = {
                @Index(name = "idx_trades_user_symbol", columnList = "USER_ID, SYMBOL"),
                @Index(name = "idx_trades_user_time", columnList = "USER_ID, TRADED_AT")
        }
)
@Getter
@NoArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRADE_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "SYMBOL", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "SIDE", nullable = false, length = 10)
    private TradeSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", nullable = false, length = 10)
    private OrderKind kind;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    // 해외주식 USD 가격: 소수점 고려
    @Column(name = "PRICE_USD", nullable = false, precision = 19, scale = 6)
    private BigDecimal priceUsd;

    // v1에서는 optional (원화 표시에 쓰고 싶으면 저장)
    @Column(name = "USD_KRW_RATE", precision = 19, scale = 6)
    private BigDecimal usdKrwRate;

    @Column(name = "TRADED_AT", nullable = false)
    private LocalDateTime tradedAt;

    public static Trade of(
            User user,
            String symbol,
            TradeSide side,
            OrderKind kind,
            Integer quantity,
            BigDecimal priceUsd,
            BigDecimal usdKrwRate
    ) {
        Trade t = new Trade();
        t.user = user;
        t.symbol = symbol;
        t.side = side;
        t.kind = kind;
        t.quantity = quantity;
        t.priceUsd = priceUsd;
        t.usdKrwRate = usdKrwRate;
        t.tradedAt = LocalDateTime.now();
        return t;
    }
}
