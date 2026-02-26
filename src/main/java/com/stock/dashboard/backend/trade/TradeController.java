package com.stock.dashboard.backend.trade;

import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.trade.dto.CreateTradeRequest;
import com.stock.dashboard.backend.trade.dto.TradeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeService tradeService;

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody CreateTradeRequest req
    ) {
        Long id = tradeService.create(principal.getUser(), req);
        return ResponseEntity.ok(id);
    }

    //포트폴리오 페이지의 상세페이지에서 사용
    @GetMapping
    public List<TradeResponse> listBySymbol(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam String symbol
    ) {
        return tradeService.getTradesBySymbol(principal.getId(), symbol);
    }
}
