package com.stock.dashboard.backend.home.vo;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeTickerVO {
    private String symbol;
    private String name;        // "애플" 같은 표시명 (없으면 symbol만)
    private double price;
    private double change;
    private double changePercent;
    private List<Double> sparkline; // 미니 차트용 (예: 최근 30일 종가)
}