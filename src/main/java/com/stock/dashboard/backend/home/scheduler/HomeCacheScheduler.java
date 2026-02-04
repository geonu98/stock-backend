package com.stock.dashboard.backend.home.scheduler;

import com.stock.dashboard.backend.home.service.HomeService;
import com.stock.dashboard.backend.home.service.RecommendationPoolService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomeCacheScheduler {

    private final HomeService homeService;
    private final RecommendationPoolService poolService;

    @PostConstruct
    public void init() {
        try {
//            poolService.refreshPool();
            // homeService.refreshHomeCache();  // 부팅 시에는 막기
            log.info("Home cache refresh (init)");
        } catch (Exception e) {
            log.warn("Home cache refresh failed (init).", e);
        }
    }

    @Scheduled(cron = "0 0,30 0-6,23 * * *", zone = "Asia/Seoul")
    public void refreshDuringMarketHours() {
        try {
//            poolService.refreshPool();
            homeService.refreshHomeCache();
            log.info("Home cache refresh (market hours)");
        } catch (Exception e) {
            log.warn("Home cache refresh failed (market hours). Keep last success.", e);
        }
    }

    @Scheduled(cron = "0 0 9,15 * * *", zone = "Asia/Seoul")
    public void refreshOffHours() {
        try {
//            poolService.refreshPool();
            homeService.refreshHomeCache();
        } catch (Exception e) {
            log.warn("Home cache refresh failed (off hours). Keep last success.", e);
        }
    }
}
