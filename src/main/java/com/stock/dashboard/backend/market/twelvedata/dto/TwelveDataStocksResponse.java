package com.stock.dashboard.backend.market.twelvedata.dto;

import lombok.Data;
import java.util.List;

@Data
public class TwelveDataStocksResponse {
    private List<TwelveDataStockItem> data;
}
