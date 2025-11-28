package com.stock.dashboard.backend.controller;


import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import com.stock.dashboard.backend.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {
private final  MarketService marketService;


    @GetMapping("/price")
    public MarketSummaryVO getPrice (@RequestParam String symbol){
        return    marketService.getRealtimePrice(symbol);

    }

}
