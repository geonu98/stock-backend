package com.stock.dashboard.backend.market.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.backend.market.cache.RedisStringCache;
import com.stock.dashboard.backend.market.client.FinnhubClient;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID; // ✅ [추가] 락 토큰(내 락인지 판별)용

@Service
@RequiredArgsConstructor
public class MarketRealtimePriceService {

    // ✅ TTL 정책
    private static final Duration FRESH_TTL = Duration.ofSeconds(10);     // 실시간 화면용
    private static final Duration STALE_TTL = Duration.ofMinutes(10);     // 장애/레이트리밋 폴백용
    private static final Duration LOCK_TTL  = Duration.ofSeconds(10);      //

    // ✅ Redis Key Prefix
    private static final String FRESH_KEY_PREFIX = "market:quote:fresh:";
    private static final String STALE_KEY_PREFIX = "market:quote:stale:";
    private static final String LOCK_KEY_PREFIX  = "market:quote:lock:";

    private final FinnhubClient finnhubClient;
    private final RedisStringCache redisStringCache;
    private final ObjectMapper objectMapper;

    public MarketSummaryVO getRealtimePrice(String symbol) {
        String s = normalizeSymbol(symbol);

        String freshKey = FRESH_KEY_PREFIX + s;
        String staleKey = STALE_KEY_PREFIX + s;
        String lockKey  = LOCK_KEY_PREFIX + s;

        // 1) ✅ fresh 캐시 hit면 즉시 반환
        MarketSummaryVO fresh = getCached(freshKey);
        if (fresh != null) return fresh;

        // ✅ [추가] 락 value를 "고정값 1"이 아니라, UUID 토큰으로 설정
        // 이유: 락 TTL 만료 후 다른 요청이 락을 다시 잡았는데,
        //      이전 요청이 finally에서 delete(lockKey)를 해버리면 "남의 락을 지워버리는" 레이스가 생김.
        //      → 토큰 기반 + compare-and-delete로 안전하게 해제해야 함.
        String lockValue = UUID.randomUUID().toString();

        // 2) ✅ stampede 방지: 락 획득 시에만 외부 호출
        // ✅ [변경] setIfAbsent(lockKey, "1", ...) -> setIfAbsent(lockKey, lockValue, ...)
        Boolean locked = redisStringCache.setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                MarketSummaryVO fetched = fetchFromFinnhub(s);

                // fetched가 null이면 의미 없으니 fallback 시도
                if (fetched != null) {
                    // ✅ 정상 데이터면 fresh + stale 둘 다 저장
                    setCached(freshKey, fetched, FRESH_TTL);
                    setCached(staleKey, fetched, STALE_TTL);
                }

                // 반환은 fetched 우선
                if (fetched != null) return fetched;

                // ✅ [추가] fetched가 null이어도 stale이 있으면 폴백 (안정성 ↑)
                MarketSummaryVO stale = getCached(staleKey);
                if (stale != null) return stale;

            } catch (Exception e) {
                // ✅ 외부 호출 실패 시 stale 폴백
                MarketSummaryVO stale = getCached(staleKey);
                if (stale != null) return stale;

                // stale도 없으면 예외를 올려서 상위에서 partial 처리
                throw e;

            } finally {
                // 락 TTL이 짧아서 굳이 delete 안 해도 되지만, 즉시 해제하면 더 좋음
                // ✅ [변경] delete(lockKey) -> deleteIfValueMatches(lockKey, lockValue)
                // 이유: 내 락일 때만 지워야 "남의 락 삭제" 레이스를 막을 수 있음.
                redisStringCache.deleteIfValueMatches(lockKey, lockValue);
            }

            // fetched가 null이었고 stale도 없으면 여기로 올 수 있음 → 예외
            throw new IllegalStateException("Finnhub quote 응답이 비어있습니다. symbol=" + s);
        }

        // 3) ✅ 락을 못 잡았으면(다른 요청이 갱신 중)
        // - 잠깐 기다렸다가 fresh를 다시 읽거나
        // - 그냥 stale로 폴백 (가장 안정적)
        // 여기서는 “즉시 stale 폴백 → 없으면 짧게 재시도”로 구성
        MarketSummaryVO stale = getCached(staleKey);
        if (stale != null) return stale;

        // stale도 없으면 짧게 fresh 재시도(동시 갱신이 막 끝났을 수 있음)
        sleepSilently(80);
        fresh = getCached(freshKey);
        if (fresh != null) return fresh;

        // 그래도 없으면: 최후의 수단으로 예외(상위 partial 처리)
        throw new IllegalStateException("시세 캐시 미스/갱신중 및 stale 없음. symbol=" + s);
    }

    private MarketSummaryVO fetchFromFinnhub(String symbol) {
        Map<String, Object> raw = finnhubClient.getQuoteRaw(symbol);

        double price = toDouble(raw.get("c"));          // current
        double change = toDouble(raw.get("d"));         // change
        double changePercent = toDouble(raw.get("dp")); // change %
        double high = toDouble(raw.get("h"));
        double low = toDouble(raw.get("l"));
        double open = toDouble(raw.get("o"));
        double prevClose = toDouble(raw.get("pc"));

        return MarketSummaryVO.builder()
                .symbol(symbol)
                .price(price)
                .open(open)
                .high(high)
                .low(low)
                .previousClose(prevClose)
                .change(change)
                .changePercent(changePercent)
                .volume(0L)
                .build();
    }

    private MarketSummaryVO getCached(String key) {
        String json = redisStringCache.get(key);
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, MarketSummaryVO.class);
        } catch (Exception e) {
            // 깨진 캐시는 무시
            return null;
        }
    }

    private void setCached(String key, MarketSummaryVO vo, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            redisStringCache.set(key, json, ttl);
        } catch (JsonProcessingException e) {
            // 직렬화 실패면 캐시 저장만 포기
        }
    }

    private static String normalizeSymbol(String symbol) {
        String s = (symbol == null ? "" : symbol.trim().toUpperCase());
        if (s.isEmpty()) throw new IllegalArgumentException("symbol은 필수입니다.");
        return s;
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }

    private static void sleepSilently(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}