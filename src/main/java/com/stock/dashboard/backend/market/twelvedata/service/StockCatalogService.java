package com.stock.dashboard.backend.market.twelvedata.service;

import com.stock.dashboard.backend.market.client.TwelveDataStocksClient;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataStockItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockCatalogService {

    private final TwelveDataStocksClient stocksClient;

    public List<TwelveDataStockItem> getCandidatePool(int poolSize) {
        return stocksClient.fetchNasdaqStocks()
                .getData()
                .stream()
                .filter(s -> "Common Stock".equalsIgnoreCase(s.getType()))
                .limit(poolSize)
                .collect(Collectors.toList());
    }
}
