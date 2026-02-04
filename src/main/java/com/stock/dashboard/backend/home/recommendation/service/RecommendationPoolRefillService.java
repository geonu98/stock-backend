package com.stock.dashboard.backend.home.recommendation.service;

import com.stock.dashboard.backend.home.recommendation.pool.RecommendationPoolRepository;
import com.stock.dashboard.backend.market.twelvedata.dto.TwelveDataStockItem;
import com.stock.dashboard.backend.market.twelvedata.service.StockCatalogService;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationPoolRefillService {

    private final RecommendationPoolRepository poolRepository;
    private final StockCatalogService stockCatalogService;

    private static final int POOL_TARGET = 20;
    private static final int REFILL_BATCH = 5;

    @Value("${home.recommend.candidate-pool:120}")
    private int candidatePool;

    @Value("${home.recommend.max-attempts:200}")
    private int maxAttempts;

    @Async
    public void warmUpFillToTargetAsync(String version) {
        for (int i = 0; i < 10; i++) {
            int size = poolRepository.size(version);
            if (size >= POOL_TARGET) {
                log.info("[POOL] warm-up done. size={}", size);
                return;
            }

            refillOnceNoCooldown(version);

            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
    }

    private void refillOnceNoCooldown(String version) {
        long now = Instant.now().getEpochSecond();

        if (!poolRepository.tryLock(version)) return;

        try {
            int currentSize = poolRepository.size(version);
            if (currentSize >= POOL_TARGET) return;

            int need = Math.min(REFILL_BATCH, POOL_TARGET - currentSize);
            log.info("[POOL] warm-up refill start. need={}", need);

            List<String> symbols = pickSymbols(need);

            int added = poolRepository.addAllUnique(version, symbols);
            poolRepository.updateLastRunAt(version, now);

            log.info("[POOL] warm-up refill done. added={}, size={}", added, poolRepository.size(version));
        } finally {
            poolRepository.unlock(version);
        }
    }

    @Async
    public void refillAsync(String version) {
        long now = Instant.now().getEpochSecond();

        if (!poolRepository.tryLock(version)) return;

        try {
            if (!poolRepository.isCooldownPassed(version, now)) return;

            int currentSize = poolRepository.size(version);
            if (currentSize >= POOL_TARGET) return;

            int need = Math.min(REFILL_BATCH, POOL_TARGET - currentSize);

            List<String> symbols = pickSymbols(need);

            poolRepository.addAllUnique(version, symbols);
            poolRepository.updateLastRunAt(version, now);
        } finally {
            poolRepository.unlock(version);
        }
    }

    private List<String> pickSymbols(int need) {
        int target = Math.max(0, need);
        if (target == 0) return List.of();

        List<TwelveDataStockItem> pool = stockCatalogService.getCandidatePool(candidatePool);
        if (pool == null || pool.isEmpty()) return List.of();

        List<TwelveDataStockItem> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);

        List<String> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int attempts = 0;

        for (TwelveDataStockItem it : shuffled) {
            if (out.size() >= target) break;
            if (attempts >= maxAttempts) break;

            attempts++;

            String sym = (it == null) ? null : it.getSymbol();
            if (!isSafeCommonStockSymbol(sym)) continue;

            String upper = sym.trim().toUpperCase();
            if (used.add(upper)) out.add(upper);
        }

        return out;
    }

    private boolean isSafeCommonStockSymbol(String symbol) {
        if (symbol == null) return false;
        String s = symbol.trim().toUpperCase();
        return s.matches("^[A-Z]{1,6}$");
    }
}
