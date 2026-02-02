package com.stock.dashboard.backend.home.scheduler;

import com.stock.dashboard.backend.home.service.HomeService;
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

    @PostConstruct
    public void init() {
        try {
            homeService.refreshHomeCache();
            log.info("Home cache refresh (init)");
        } catch (Exception e) {
            log.warn("Home cache refresh failed (init).", e);
        }
    }

    // 정규장 시간대(한국 기준 23:30~06:00): 30분마다
    // 23:30, 00:00, 00:30, ... , 05:30, 06:00
    @Scheduled(cron = "0 0,30 0-6,23 * * *", zone = "Asia/Seoul")
    public void refreshDuringMarketHours() {
        try {
            homeService.refreshHomeCache();
            log.info("Home cache refresh (market hours)");

        } catch (Exception e) {
            log.warn("Home cache refresh failed (market hours). Keep last success.", e);
        }
    }

    // 장 닫힌 시간대: 하루 2번(예: 09:00, 15:00)
    @Scheduled(cron = "0 0 9,15 * * *", zone = "Asia/Seoul")
    public void refreshOffHours() {
        try {
            homeService.refreshHomeCache();
        } catch (Exception e) {
            log.warn("Home cache refresh failed (off hours). Keep last success.", e);
        }
    }
}
