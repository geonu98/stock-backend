package com.stock.dashboard.backend.trade;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.trade.dto.CreateTradeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;

    public Long create(User user, CreateTradeRequest req) {
        validate(req);

        Trade trade = Trade.of(
                user,
                req.getSymbol().trim().toUpperCase(),
                req.getSide(),
                req.getKind(),
                req.getQuantity(),
                req.getPriceUsd(),
                req.getUsdKrwRate()
        );

        return tradeRepository.save(trade).getId();
    }

    private void validate(CreateTradeRequest req) {
        if (req == null) throw new IllegalArgumentException("요청이 비었습니다.");
        if (!StringUtils.hasText(req.getSymbol())) throw new IllegalArgumentException("symbol 필수");
        if (req.getSide() == null) throw new IllegalArgumentException("side 필수");
        if (req.getKind() == null) throw new IllegalArgumentException("kind 필수");

        if (req.getQuantity() == null || req.getQuantity() <= 0)
            throw new IllegalArgumentException("quantity는 1 이상");

        if (req.getPriceUsd() == null || req.getPriceUsd().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("priceUsd는 0 초과");
    }
}
