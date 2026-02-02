package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.home.vo.HomeSnapshot;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class HomeCacheStore {

    private final AtomicReference<HomeSnapshot> homeCache = new AtomicReference<>();

    @Getter
    private volatile Instant lastSuccessAt = null;

    public HomeSnapshot get() {
        return homeCache.get();
    }

    public void set(HomeSnapshot snapshot) {
        homeCache.set(snapshot);
        lastSuccessAt = Instant.now();
    }

    public boolean hasValue() {
        return homeCache.get() != null;
    }
}
