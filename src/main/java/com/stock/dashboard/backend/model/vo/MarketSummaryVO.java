package com.stock.dashboard.backend.model.vo;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketSummaryVO {
    private String symbol;      // 종목 코드
    private double price;       // 현재가
    private double high;        // 고가
    private double low;         // 저가
    private double open;        // 시가
    private double previousClose; // 전일 종가
    private double change;      // 전일 대비 금액
    private double changePercent; // 전일 대비 퍼센트
    private long volume;        // 거래량
}
