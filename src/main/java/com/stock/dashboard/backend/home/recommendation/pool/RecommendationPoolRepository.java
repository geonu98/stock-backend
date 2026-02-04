package com.stock.dashboard.backend.home.recommendation.pool;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RecommendationPoolRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 정책 상수(파트3 기준)
    private static final int POOL_TTL_HOURS = 48;       // 전날 풀 임시 사용 대비
    private static final long LOCK_TTL_SEC = 60;        // 리필 락
    private static final long COOLDOWN_SEC = 600;       // 10분

    private String poolListKey(String v) { return "home:rec:pool:" + v + ":list"; }
    private String poolSetKey(String v)  { return "home:rec:pool:" + v + ":set"; }
    private String lockKey(String v)     { return "home:rec:refill:" + v + ":lock"; }
    private String lastRunKey(String v)  { return "home:rec:refill:" + v + ":lastRunAt"; }

    public String todayVersion() {
        return LocalDate.now(KST).format(VERSION_FMT);
    }

    public String yesterdayVersion() {
        return LocalDate.now(KST).minusDays(1).format(VERSION_FMT);
    }

    public int size(String v) {
        Long size = redisTemplate.opsForList().size(poolListKey(v));
        return size == null ? 0 : size.intValue();
    }

    @SuppressWarnings("unchecked")
    public List<String> range(String v, int offset, int limit) {
        List<Object> raw = redisTemplate.opsForList()
                .range(poolListKey(v), offset, offset + limit - 1);

        if (raw == null) return List.of();

        List<String> result = new ArrayList<>();
        for (Object o : raw) result.add((String) o);
        return result;
    }

    public int addAllUnique(String v, List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return 0;

        int added = 0;
        for (String symbol : symbols) {
            Long addedCount = redisTemplate.opsForSet().add(poolSetKey(v), symbol);
            if (addedCount != null && addedCount > 0) {
                redisTemplate.opsForList().rightPush(poolListKey(v), symbol);
                added++;
            }
        }
        touchTtl(v);
        return added;
    }


    public boolean tryLock(String v) {
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(v), "1", LOCK_TTL_SEC, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String v) {
        redisTemplate.delete(lockKey(v));
    }

    public boolean isCooldownPassed(String v, long nowEpochSec) {
        Object val = redisTemplate.opsForValue().get(lastRunKey(v));
        if (val == null) return true;

        long last = Long.parseLong(val.toString());
        return (nowEpochSec - last) >= COOLDOWN_SEC;
    }

    public void updateLastRunAt(String v, long nowEpochSec) {
        redisTemplate.opsForValue().set(lastRunKey(v), String.valueOf(nowEpochSec));
        redisTemplate.expire(lastRunKey(v), POOL_TTL_HOURS, TimeUnit.HOURS);
    }

    private void touchTtl(String v) {
        redisTemplate.expire(poolListKey(v), POOL_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(poolSetKey(v), POOL_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(lastRunKey(v), POOL_TTL_HOURS, TimeUnit.HOURS);
    }
}
