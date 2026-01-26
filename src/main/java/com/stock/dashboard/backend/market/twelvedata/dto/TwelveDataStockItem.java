package com.stock.dashboard.backend.market.twelvedata.dto;

import lombok.Data;

@Data
public class TwelveDataStockItem {
    private String symbol;
    private String name;
    private String exchange;
    private String type;
}
