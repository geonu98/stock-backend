package com.stock.dashboard.backend.service.oauth;

import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Kakao OAuth 전용 Adapter
 * - Kakao에서 사용자 정보를 가져오고
 * - SocialUserInfo(공통 모델)로 변환하여 반환
 * - DB 저장 및 JWT 발급은 하지 않음
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthService implements SocialOAuthService {

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 해당 Provider 문자열이 Kakao인지 확인
     * SocialLoginService에서 Adapter 선택할 때 사용
     */
    @Override
    public boolean supports(String provider) {
        return provider.equalsIgnoreCase("kakao");
    }

    /**
     * code로 AccessToken 요청 후 사용자 정보 획득
     */
    @Override
    public SocialUserInfo getUserInfo(String code) {
        String accessToken = requestAccessToken(code);
        return requestKakaoUserInfo(accessToken);
    }

    /**
     * Authorization Code → Access Token 교환 요청
     */
    private String requestAccessToken(String code) {
        String url = "https://kauth.kakao.com/oauth/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        if (clientSecret != null && !clientSecret.isBlank()) {
            params.add("client_secret", clientSecret);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(params, headers);
        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }

    /**
     * Access Token으로 카카오 사용자 정보 요청
     * → SocialUserInfo 형태로 변환하여 반환
     */
    private SocialUserInfo requestKakaoUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        Map<String, Object> body = response.getBody();
        log.info("Kakao 사용자 정보: {}", body);

        Long id = ((Number) body.get("id")).longValue();
        Map<String, Object> kakaoAccount =
                (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> profile =
                (Map<String, Object>) (kakaoAccount != null ?
                        kakaoAccount.get("profile") : null);

        return new SocialUserInfo(
                "kakao",
                String.valueOf(id),
                kakaoAccount != null ? (String) kakaoAccount.get("email") : null,
                profile != null ? (String) profile.get("nickname") : null,
                profile != null ? (String) profile.get("profile_image_url") : null
        );
    }
}
