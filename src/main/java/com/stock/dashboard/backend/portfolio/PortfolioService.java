package com.stock.dashboard.backend.portfolio;

import com.stock.dashboard.backend.exception.BadRequestException;
import com.stock.dashboard.backend.market.bok.BokExchangeRateService;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import com.stock.dashboard.backend.portfolio.dto.*;
import com.stock.dashboard.backend.trade.*;
import static com.stock.dashboard.backend.portfolio.PortfolioWarningCodes.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final TradeRepository tradeRepository;
    private final MarketRealtimePriceService marketRealtimePriceService;
    private final BokExchangeRateService bokExchangeRateService;



    public PortfolioResponse getPortfolio(Long userId) {

        // ✅ Partial 성공용 경고 목록 (표준화 코드 + symbol)
        List<WarningResponse> warnings = new ArrayList<>();

        List<Trade> trades =
                tradeRepository.findByUser_IdOrderByTradedAtAsc(userId);

        Map<String, List<Trade>> bySymbol = new HashMap<>();
        for (Trade t : trades) {
            bySymbol.computeIfAbsent(t.getSymbol(), k -> new ArrayList<>()).add(t);
        }

        List<PositionResponse> positions = new ArrayList<>();

        BigDecimal totalMarketValueUsd = BigDecimal.ZERO;
        BigDecimal totalUnrealizedUsd = BigDecimal.ZERO;
        BigDecimal totalRealizedUsd = BigDecimal.ZERO;
        BigDecimal totalCostUsd = BigDecimal.ZERO;

        for (String symbol : bySymbol.keySet()) {

            PositionCalc calc = calculate(bySymbol.get(symbol));
            if (calc.quantity <= 0) continue;

            BigDecimal costUsd = calc.avgCost.multiply(bd(calc.quantity));
            totalCostUsd = totalCostUsd.add(costUsd);

            // =========================
            // ✅ 시세 조회 방어 (Partial 정책)
            // - 실패하면 이 심볼은 positions/summary에서 제외
            // - 대신 warnings에 (code, symbol) 기록
            // =========================
            MarketSummaryVO quote;
            try {
                quote = marketRealtimePriceService.getRealtimePrice(symbol);
            } catch (Exception e) {
                warnings.add(new WarningResponse(QUOTE_UNAVAILABLE, symbol));
                continue;
            }

            if (quote == null) {
                warnings.add(new WarningResponse(QUOTE_UNAVAILABLE, symbol));
                continue;
            }

            double priceRaw = quote.getPrice(); // primitive double
            if (Double.isNaN(priceRaw) || Double.isInfinite(priceRaw) || priceRaw <= 0) {
                warnings.add(new WarningResponse(INVALID_QUOTE_PRICE, symbol));
                continue;
            }

            BigDecimal currentUsd = bd(priceRaw);

            BigDecimal marketValueUsd =
                    currentUsd.multiply(bd(calc.quantity));

            BigDecimal unrealizedUsd =
                    currentUsd.subtract(calc.avgCost)
                            .multiply(bd(calc.quantity));

            BigDecimal unrealizedReturnPct =
                    calc.avgCost.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : currentUsd.subtract(calc.avgCost)
                            .divide(calc.avgCost, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

            positions.add(new PositionResponse(
                    symbol,
                    calc.quantity,
                    scale6(calc.avgCost),
                    scale6(currentUsd),
                    scale2(marketValueUsd),
                    scale2(unrealizedUsd),
                    scale2(calc.realized),
                    scale2(unrealizedReturnPct)
            ));

            totalMarketValueUsd = totalMarketValueUsd.add(marketValueUsd);
            totalUnrealizedUsd = totalUnrealizedUsd.add(unrealizedUsd);
            totalRealizedUsd = totalRealizedUsd.add(calc.realized);
        }

        BigDecimal totalPnlUsd = totalUnrealizedUsd.add(totalRealizedUsd);

        BigDecimal totalReturnPct =
                totalCostUsd.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : totalPnlUsd
                        .divide(totalCostUsd, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        // =========================
        // ✅ KRW 환산 (Partial 정책)
        // - 환율 실패하면: KRW 관련 필드만 null
        // - USD summary는 정상 유지
        // - warnings에 (FX_RATE_UNAVAILABLE, null) 기록
        // =========================
        BigDecimal usdKrwRate = null;
        BigDecimal totalMarketValueKrw = null;
        BigDecimal totalPnlKrw = null;

        try {
            Double rateRaw = bokExchangeRateService.getUsdKrwRate();
            if (rateRaw == null || rateRaw <= 0) {
                warnings.add(new WarningResponse(FX_RATE_UNAVAILABLE, null));
            } else {
                usdKrwRate = scale2(BigDecimal.valueOf(rateRaw));
                totalMarketValueKrw = scale0(totalMarketValueUsd.multiply(usdKrwRate));
                totalPnlKrw = scale0(totalPnlUsd.multiply(usdKrwRate));
            }
        } catch (Exception e) {
            warnings.add(new WarningResponse(FX_RATE_UNAVAILABLE, null));
        }

        SummaryResponse summary = new SummaryResponse(
                scale2(totalMarketValueUsd),
                scale2(totalUnrealizedUsd),
                scale2(totalRealizedUsd),
                scale2(totalPnlUsd),

                scale2(totalCostUsd),
                scale2(totalReturnPct),

                usdKrwRate,            // ✅ 실패 시 null
                totalMarketValueKrw,   // ✅ 실패 시 null
                totalPnlKrw            // ✅ 실패 시 null
        );

        // ✅ 정렬 NPE 방어
        positions.sort(
                Comparator.comparing(
                                PositionResponse::marketValueUsd,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .reversed()
        );

        return new PortfolioResponse(positions, summary, warnings);
    }

    private PositionCalc calculate(List<Trade> trades) {

        int quantity = 0;
        BigDecimal costAmount = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;

        for (Trade t : trades) {

            int q = t.getQuantity();
            BigDecimal price = t.getPriceUsd();

            if (t.getSide() == TradeSide.BUY) {

                quantity += q;
                costAmount = costAmount.add(price.multiply(bd(q)));

            } else {

                if (q > quantity) {
                    //  [변경] 예외 타입 통일 (GlobalExceptionHandler가 BAD_REQUEST로 표준 응답)
                    throw new BadRequestException(
                            "SELL 수량이 보유수량보다 큽니다. symbol=" + t.getSymbol()
                    );
                }

                BigDecimal avg =
                        costAmount.divide(bd(quantity), 10, RoundingMode.HALF_UP);

                BigDecimal pnl =
                        price.subtract(avg).multiply(bd(q));

                realized = realized.add(pnl);

                quantity -= q;
                costAmount =
                        costAmount.subtract(avg.multiply(bd(q)));
            }
        }

        BigDecimal avgCost =
                quantity == 0
                        ? BigDecimal.ZERO
                        : costAmount.divide(bd(quantity), 10, RoundingMode.HALF_UP);

        return new PositionCalc(quantity, avgCost, realized);
    }

    private record PositionCalc(
            int quantity,
            BigDecimal avgCost,
            BigDecimal realized
    ) {}

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale6(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale0(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }
}