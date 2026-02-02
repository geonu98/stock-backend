package com.stock.dashboard.backend.market.twelvedata.service;

import com.stock.dashboard.backend.market.client.TwelveDataStocksClient;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataStockItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCatalogService {

    private final TwelveDataStocksClient stocksClient;

    public List<TwelveDataStockItem> getCandidatePool(int poolSize) {
        var all = stocksClient.fetchNasdaqStocks().getData();
        if (all == null || all.isEmpty()) {
            return List.of();
        }

        log.info("nasdaq total={}", all.size());

        // 1) Common Stock만
        List<TwelveDataStockItem> common = all.stream()
                .filter(s -> isCommonStock(s.getType()))
                .collect(Collectors.toList());

        log.info("nasdaq common total={}", common.size());

        // 2) 안전 심볼만 (너가 이미 추천쪽에서 쓰는 정책과 통일)
        List<TwelveDataStockItem> safe = common.stream()
                .filter(s -> isSafeCommonStockSymbol(s.getSymbol()))
                .collect(Collectors.toList());

        log.info("nasdaq safe common total={}", safe.size());

        // 3) 전체에서 섞어서 샘플링
        List<TwelveDataStockItem> shuffled = new ArrayList<>(safe);
        Collections.shuffle(shuffled);

        int end = Math.min(poolSize, shuffled.size());
        List<TwelveDataStockItem> picked = shuffled.subList(0, end);

        // 디버그: 샘플이 진짜 섞였는지 앞 30개만 찍기
        picked.stream().limit(30).forEach(s ->
                log.info("picked sym={} type={}", s.getSymbol(), s.getType())
        );

        return picked;
    }

    private boolean isCommonStock(String type) {
        if (type == null) return false;
        return "common stock".equalsIgnoreCase(type.trim());
    }

    private boolean isSafeCommonStockSymbol(String symbol) {
        if (symbol == null) return false;
        String s = symbol.trim().toUpperCase(Locale.US);
        return s.matches("^[A-Z]{1,5}$");
    }
}
