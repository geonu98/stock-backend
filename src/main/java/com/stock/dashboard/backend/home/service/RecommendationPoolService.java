package com.stock.dashboard.backend.home.service;

import com.stock.dashboard.backend.exception.TwelveDataRateLimitException;
import com.stock.dashboard.backend.home.dto.RecommendedItemResponse;
import com.stock.dashboard.backend.home.dto.RecommendationsResponse;
import com.stock.dashboard.backend.home.recommendation.pool.RecommendationPoolRepository;
import com.stock.dashboard.backend.home.recommendation.service.RecommendationPoolRefillService;
import com.stock.dashboard.backend.market.service.MarketRealtimePriceService;
import com.stock.dashboard.backend.market.twelvedata.dto.SparklinePoint;
import com.stock.dashboard.backend.market.twelvedata.service.SparklineService;
import com.stock.dashboard.backend.model.vo.MarketSummaryVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationPoolService {

    private final RecommendationPoolRepository poolRepository;
    private final RecommendationPoolRefillService refillService;

    private final MarketRealtimePriceService marketRealtimePriceService;
    private final SparklineService sparklineService;

    @Value("${home.recommend.page-size:10}")
    private int pageSize;

    @Value("${home.recommend.pool-target:20}")
    private int poolTarget;

    @Value("${home.recommend.refill-trigger-threshold:10}")
    private int refillTriggerThreshold;

    /**
     * 홈/더보기에서 "같은 세트"를 보게 하기 위한 기준 버전.
     * - 기본은 todayVersion
     * - today가 비어있으면 yesterdayVersion으로 폴백
     */
    public String currentVersion() {
        String today = poolRepository.todayVersion();
        int todaySize = poolRepository.size(today);
        return (todaySize > 0) ? today : poolRepository.yesterdayVersion();
    }

    /**
     * 더보기 전용 (POOL only)
     * - version을 지정하면, 그 version에서만 페이지를 읽는다 (홈과 더보기 일치 목적)
     *
     * 동작 요약:
     * - total <= 0 이거나 start >= total 이면 빈 결과 + (today 부족 시) refill 트리거
     * - version이 today면 부족할 때 refill 트리거
     * - 스파크라인/시세 변환 실패가 있을 수 있어 pageSize를 채우기 위해 max 3*pageSize까지 스캔
     * - TwelveData rate limit 맞으면 즉시 중단하고 nextOffset을 유지(재시도 유도)
     */
    public RecommendationsResponse getRecommendationsFromPool(String version, int offset) {

        int start = Math.max(0, offset);
        String v = (version == null || version.isBlank()) ? currentVersion() : version;

        // 버전 total
        int total = poolRepository.size(v);
        log.info("[POOL DEBUG] version={} total={} offset={}", v, total, offset);

        // todaySize는 refill 트리거 판단에만 사용
        String today = poolRepository.todayVersion();
        int todaySize = poolRepository.size(today);

        if (total <= 0 || start >= total) {
            // today가 비어있거나 부족하면 리필 트리거 (백그라운드)
            if (todaySize < poolTarget) {
                triggerRefillIfNeeded(today, todaySize);
            }
            return new RecommendationsResponse(List.of(), null);
        }

        // "현재 버전이 today"면 부족할 때 리필 트리거
        if (Objects.equals(v, today)) {
            triggerRefillIfNeeded(today, todaySize);
        }

        List<RecommendedItemResponse> items = new ArrayList<>();
        int cursor = start;
        boolean hitRateLimit = false;

        // 변환 실패(스파크라인 없음/quote 실패) 때문에 pageSize가 덜 채워질 수 있어서
        // "조금 더" 읽어서 채우는 방식 (최대 3번 * pageSize)
        int maxScan = Math.min(total, start + pageSize * 3);

        while (cursor < maxScan && items.size() < pageSize && !hitRateLimit) {
            int fetch = Math.min(pageSize, maxScan - cursor);
            List<String> symbols = poolRepository.range(v, cursor, fetch);

            if (symbols.isEmpty()) break;

            for (String sym : symbols) {
                if (items.size() >= pageSize) break;

                try {
                    RecommendedItemResponse r = buildItemSafe(sym);
                    if (r != null) items.add(r);

                } catch (TwelveDataRateLimitException e) {
                    hitRateLimit = true;
                    log.warn("[POOL] read stop due to rate limit. symbol={} msg={}", sym, e.getMessage());
                    break;

                } catch (Exception e) {
                    log.debug("[POOL] read skip symbol={} ex={} msg={}",
                            sym, e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // 실제로 range()가 돌려준 개수만큼 전진
            cursor += symbols.size();
        }

        Integer nextOffset = (cursor < total && !items.isEmpty()) ? cursor : null;

        // rate limit이면 nextOffset은 유지해도 되지만, UX상 “잠깐 뒤 재시도”가 맞으니 cursor 남겨둠
        if (hitRateLimit && cursor < total) {
            nextOffset = cursor;
        }

        return new RecommendationsResponse(items, nextOffset);
    }

    /**
     * 기존 시그니처는 남겨두고, 내부적으로 currentVersion()을 사용하도록 wrapper 처리
     * (기존 호출부 깨지지 않게)
     */
    public RecommendationsResponse getRecommendationsFromPool(int offset) {
        return getRecommendationsFromPool(currentVersion(), offset);
    }

    /**
     * 홈 추천 (5개)
     * - version을 받아서 홈과 더보기의 "세트"를 일치시키기 위함
     */
    public List<RecommendedItemResponse> getRecommendationsForHome(String version) {
        RecommendationsResponse page0 = getRecommendationsFromPool(version, 0);
        List<RecommendedItemResponse> items =
                (page0 == null || page0.items() == null) ? List.of() : page0.items();

        int end = Math.min(5, items.size());
        return items.subList(0, end);
    }

    /**
     * 기존 시그니처 유지 (호환)
     */
    public List<RecommendedItemResponse> getRecommendationsForHome() {
        return getRecommendationsForHome(currentVersion());
    }

    private void triggerRefillIfNeeded(String todayVersion, int todaySize) {
        // 너무 자주 쏘지 않게 threshold 기준만 체크 (쿨다운/락은 RefillService/Repo가 최종 방어)
        if (todaySize < refillTriggerThreshold) {
            log.info("[POOL] trigger refill async. todaySize={}", todaySize);
            refillService.refillAsync(todayVersion);
        }
    }

    private RecommendedItemResponse buildItemSafe(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        String sym = symbol.trim().toUpperCase();

        MarketSummaryVO quote = marketRealtimePriceService.getRealtimePrice(sym);

        List<SparklinePoint> sparkline = sparklineService.getSparklineOnly(sym);
        if (sparkline == null || sparkline.isEmpty()) return null;

        return new RecommendedItemResponse(
                sym,
                quote.getPrice(),
                quote.getChangePercent(),
                sparkline
        );
    }
}
