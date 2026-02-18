package com.stock.dashboard.backend.market.bok;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BokExchangeRateService {

    @Value("${bok.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    private static final DateTimeFormatter FMT = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * USD/KRW 환율 조회 (한국은행 ECOS)
     * - 공휴일/주말/발표 전: "최근 영업일" 값 반환
     */
    public Double getUsdKrwRate() {
        // ✅ 오늘 데이터가 없을 수 있으니 최근 범위를 조회
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(14);

        String url = String.format(
                "https://ecos.bok.or.kr/api/StatisticSearch/%s/json/kr/1/100/731Y001/D/%s/%s/0000001",
                apiKey,
                start.format(FMT),
                end.format(FMT)
        );

        // 🔒 키 포함된 URL 전체 로그 찍지 말기 (키 유출 위험)
        log.info("[BOK] FX request range: {} ~ {}", start, end);

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                throw new IllegalStateException("한국은행 환율 API 응답이 없습니다.");
            }

            Map<String, Object> statisticSearch = (Map<String, Object>) response.get("StatisticSearch");

            // ✅ ECOS 에러 응답 방어 (키 문제/요청 파라미터 오류 등)
            if (statisticSearch == null) {
                log.error("[BOK] Error response body: {}", response);
                throw new IllegalStateException("한국은행 환율 데이터를 가져오지 못했습니다.");
            }

            List<Map<String, Object>> rows = (List<Map<String, Object>>) statisticSearch.get("row");

            // ✅ 공휴일/주말/업데이트 전이면 row가 비거나 없을 수 있음
            if (rows == null || rows.isEmpty()) {
                throw new IllegalStateException("최근 기간 내 환율 데이터가 비어 있습니다. (휴일/업데이트 지연 가능)");
            }

            // ✅ 마지막 row = 가장 최근 영업일 데이터일 확률이 가장 높음
            Map<String, Object> lastRow = rows.get(rows.size() - 1);
            Object value = lastRow.get("DATA_VALUE");

            if (value == null) {
                throw new IllegalStateException("환율 DATA_VALUE가 없습니다.");
            }

            return Double.parseDouble(String.valueOf(value));

        } catch (RestClientResponseException e) {
            // HTTP 상태코드/응답 바디 로그
            log.error("[BOK] HTTP error: status={}, body={}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("한국은행 환율 API HTTP 오류: " + e.getRawStatusCode(), e);

        } catch (Exception e) {
            log.error("[BOK] FX fetch failed", e);
            throw e;
        }
    }
}
