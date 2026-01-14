package com.stock.dashboard.backend.service.oauth;

import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Google OAuth 전용 Adapter
 *
 * ✅ 역할
 * - Google에서 access_token을 발급받고
 * - userinfo endpoint를 호출해서 사용자 정보를 가져온 뒤
 * - SocialUserInfo(공통 DTO)로 변환하여 반환
 *
 * ✅ 원칙
 * - DB 저장 / JWT 발급은 여기서 하지 않는다. (SocialLoginService가 담당)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService implements SocialOAuthService {

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    // 프론트 주소를 받아서 redirect_uri를 "코드로" 고정 생성 (카카오랑 동일 컨셉)
    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean supports(String provider) {
        return provider.equalsIgnoreCase("google");
    }

    @Override
    public SocialUserInfo getUserInfo(String code) {
        String accessToken = requestAccessToken(code);
        return requestGoogleUserInfo(accessToken);
    }

    /**
     * ✅ authorize URL 생성
     * - 프론트에서 직접 만들고 있긴 하지만,
     *   백엔드 방식(B 방식)으로 바꾸고 싶으면 이걸 사용하면 된다.
     */
    @Override
    public String getAuthorizeUrl(String redirectUri) {
        String scope = URLEncoder.encode("openid email profile", StandardCharsets.UTF_8);

        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=" + scope
                + "&access_type=offline"
                + "&prompt=consent";
        // state는 프론트에서 이미 "google"로 넣고 있으니 여기선 생략
    }

    /**
     * ✅ code -> access_token 교환
     */
    private String requestAccessToken(String code) {
        String url = "https://oauth2.googleapis.com/token";

        // ✅ authorize 때와 "완전히 동일한" redirect_uri 사용
        // 지금 너 프론트가 /oauth/callback로 받고 있으므로 백엔드도 똑같이 맞춰야 함
        String redirectUri = frontendUrl + "/oauth/callback";
        log.info("[GOOGLE] token redirect_uri={}", redirectUri);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, entity, Map.class);

        Map body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            log.error("[GOOGLE] token response invalid: {}", body);
            throw new IllegalStateException("Google access_token not found");
        }

        return (String) body.get("access_token");
    }

    /**
     * ✅ access_token으로 userinfo 조회
     *
     * endpoint는 둘 중 아무거나 써도 되는데,
     * 여기서는 OIDC 표준 endpoint 사용:
     * - https://openidconnect.googleapis.com/v1/userinfo
     */
    private SocialUserInfo requestGoogleUserInfo(String accessToken) {
        String url = "https://openidconnect.googleapis.com/v1/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        Map<String, Object> body = response.getBody();
        log.info("[GOOGLE] userinfo: {}", body);

        if (body == null) {
            throw new IllegalStateException("Google userinfo response is null");
        }

        // Google OIDC userinfo 주요 필드
        // sub: 유저 고유 ID (providerId로 사용)
        String sub = (String) body.get("sub");
        String email = (String) body.get("email");
        String name = (String) body.get("name");
        String picture = (String) body.get("picture");

        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("Google userinfo missing sub");
        }

        return new SocialUserInfo(
                "google",
                sub,
                email,
                name,
                picture
        );
    }
}
