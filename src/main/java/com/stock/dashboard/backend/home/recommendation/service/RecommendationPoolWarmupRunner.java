package com.stock.dashboard.backend.home.recommendation.service;

import com.stock.dashboard.backend.home.recommendation.pool.RecommendationPoolRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationPoolWarmupRunner {

    private final RecommendationPoolRepository poolRepository;
    private final RecommendationPoolRefillService refillService;

    @PostConstruct
    public void warmUp() {
        String version = poolRepository.todayVersion();
        int size = poolRepository.size(version);

        if (size >= 20) {
            log.info("[POOL] warm-up skip. size={}", size);
            return;
        }

        log.info("[POOL] warm-up start. size={}", size);
        refillService.warmUpFillToTargetAsync(version);
    }
}
