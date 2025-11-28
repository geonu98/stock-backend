package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.model.vo.MarketSummaryVO;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class MarketService {

    @Value("${ALPHAVANTAGE_API_KEY}")
    private String apiKey ;
    private RestTemplate restTemplate = new RestTemplate();

    public MarketSummaryVO getRealtimePrice(String symbol) {

        try {
            String url = String.format("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s"
                    ,symbol,apiKey);

             String response = restTemplate.getForObject(url, String.class);

            JSONObject root = new JSONObject(response);

            if (!root.has("Global Quote")){
                log.error("Global Quote 없음");
                return  null;
            }
            JSONObject quote = root.getJSONObject("Global Quote");
            MarketSummaryVO marketSummaryVO = MarketSummaryVO.builder()
                    .symbol(quote.optString("01. symbol"))
                    .open(quote.optDouble("02. open", 0.0))
                    .high(quote.optDouble("03. high", 0.0))
                    .low(quote.optDouble("04. low", 0.0))
                    .price(quote.optDouble("05. price", 0.0))
                    .volume(quote.optLong("06. volume", 0))
                    .previousClose(quote.optDouble("08. previous close", 0.0))
                    .change(quote.optDouble("09. change", 0.0))
                    .changePercent(parsePercent(
                            quote.optString("10. change percent")
                    ))
                    .build();

            return marketSummaryVO;


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private double parsePercent(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }

        try {
            //% 잇음
            return Double.parseDouble(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0; // 잘못된 형식이어도 기본값 반환
        }
    }


}
