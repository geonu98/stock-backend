package com.stock.dashboard.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResendEmailClient {

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from}")
    private String from;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendHtml(String toEmail, String subject, String html) {
        String url = "https://api.resend.com/emails";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "from", from,                 // 예: "Stock Dashboard <onboarding@resend.dev>"
                "to", List.of(toEmail),
                "subject", subject,
                "html", html
        );

        try {
            ResponseEntity<String> res = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (!res.getStatusCode().is2xxSuccessful()) {
                log.error("[RESEND] send failed status={}, body={}", res.getStatusCode(), res.getBody());
                throw new IllegalStateException("Resend send failed");
            }

            log.info("[RESEND] sent ok to={} status={}", toEmail, res.getStatusCode());

        } catch (RestClientResponseException e) {
            // Resend가 내려준 에러 바디를 로그로 남기면 원인 파악이 쉬움
            log.error("[RESEND] send failed status={}, body={}", e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            throw new IllegalStateException("Failed to send verification email via Resend", e);
        } catch (Exception e) {
            log.error("[RESEND] send failed (unexpected)", e);
            throw new IllegalStateException("Failed to send verification email via Resend", e);
        }
    }
}
