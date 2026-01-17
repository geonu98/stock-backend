package com.stock.dashboard.backend.market.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DailyCandleDTO {
    private String date;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
