package com.stock.dashboard.backend.home.recommendation.service;

import com.stock.dashboard.backend.home.recommendation.pool.RecommendationPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationPoolWarmupRunner {

    private final RecommendationPoolRepository poolRepository;
    private final RecommendationPoolRefillService refillService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            String version = poolRepository.todayVersion();
            int size = poolRepository.size(version);

            if (size >= 20) {
                log.info("[POOL] warm-up skip. size={}", size);
                return;
            }

            log.info("[POOL] warm-up start. size={}", size);
            refillService.warmUpFillToTargetAsync(version);

        } catch (Exception e) {
            log.error("[POOL] warm-up failed. server will continue.", e);
        }
    }
}
