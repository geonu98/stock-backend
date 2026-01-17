package com.stock.dashboard.backend.market.bok;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    //  HttpConfig에 등록된 Bean 사용
    private final RestTemplate restTemplate;

    /**
     * USD/KRW 환율 조회 (한국은행 ECOS)
     * - 통계표: 731Y001 (주요국 통화의 대원화환율)
     * - 항목: 0000001 (미국 달러)
     * - 주기: 일(D)
     */
    public Double getUsdKrwRate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        String url = String.format(
                "https://ecos.bok.or.kr/api/StatisticSearch/%s/json/kr/1/1/731Y001/D/%s/%s/0000001",
                apiKey,
                date,
                date
        );

        log.info("BOK URL >>> {}", url);

        Map<String, Object> response =
                restTemplate.getForObject(url, Map.class);

        if (response == null) {
            throw new IllegalStateException("한국은행 환율 API 응답이 없습니다.");
        }

        Map<String, Object> statisticSearch =
                (Map<String, Object>) response.get("StatisticSearch");

        //  ECOS 에러 응답 방어
        if (statisticSearch == null) {
            log.error("BOK ERROR RESPONSE >>> {}", response);
            throw new IllegalStateException("한국은행 환율 데이터를 가져오지 못했습니다.");
        }

        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) statisticSearch.get("row");

        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("한국은행 환율 데이터가 비어 있습니다.");
        }

        return Double.parseDouble((String) rows.get(0).get("DATA_VALUE"));
    }
}
