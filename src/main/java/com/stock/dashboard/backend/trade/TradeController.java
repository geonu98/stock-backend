package com.stock.dashboard.backend.trade;

import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.trade.dto.CreateTradeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
