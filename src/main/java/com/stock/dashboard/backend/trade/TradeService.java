package com.stock.dashboard.backend.trade;

import com.stock.dashboard.backend.exception.BadRequestException; // ✅ [추가]
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.trade.dto.CreateTradeRequest;
import com.stock.dashboard.backend.trade.dto.TradeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // ✅ [추가]
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;

    // ✅ [추가] 정석: 유스케이스 단위로 트랜잭션
    @Transactional
    public Long create(User user, CreateTradeRequest req) {
        validate(req);

        String symbol = req.getSymbol().trim().toUpperCase();

        // ✅ [추가] 정책 1) SELL은 보유수량 초과 불가 → 400(BAD_REQUEST)
        // - 컨트롤러가 아니라 서비스(유스케이스)에서 막는 게 정석
        // - ErrorResponse 포맷 통일을 위해 IllegalArgumentException 대신 BadRequestException 사용
        if (req.getSide() == TradeSide.SELL) {
            Integer holdingQty = tradeRepository.getNetQuantity(user.getId(), symbol);
            if (holdingQty == null) holdingQty = 0;

            if (holdingQty < req.getQuantity()) {
                throw new BadRequestException(
                        "보유수량 초과 매도입니다. symbol=" + symbol +
                                ", holding=" + holdingQty +
                                ", sell=" + req.getQuantity()
                );
            }
        }

        Trade trade = Trade.of(
                user,
                symbol,
                req.getSide(),
                req.getKind(),
                req.getQuantity(),
                req.getPriceUsd(),
                req.getUsdKrwRate()
        );

        return tradeRepository.save(trade).getId();
    }

    private void validate(CreateTradeRequest req) {
        // ✅ [변경] 프로젝트 에러 포맷 통일을 위해 BadRequestException 사용
        if (req == null) throw new BadRequestException("요청이 비었습니다.");
        if (!StringUtils.hasText(req.getSymbol())) throw new BadRequestException("symbol 필수");
        if (req.getSide() == null) throw new BadRequestException("side 필수");
        if (req.getKind() == null) throw new BadRequestException("kind 필수");

        if (req.getQuantity() == null || req.getQuantity() <= 0)
            throw new BadRequestException("quantity는 1 이상");

        if (req.getPriceUsd() == null || req.getPriceUsd().compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("priceUsd는 0 초과");
    }

    public List<TradeResponse> getTradesBySymbol(Long userId, String symbol) {
        return tradeRepository
                .findByUser_IdAndSymbolOrderByTradedAtDesc(userId, symbol)
                .stream()
                .map(TradeResponse::from)
                .toList();
    }
}