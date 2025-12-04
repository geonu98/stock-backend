package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.RoleName;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.repository.RoleRepository;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoOAuthService {

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 카카오 로그인 URL 생성
     */
    public String getKakaoLoginUrl() {
        return "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code";
    }

    /**
     * 카카오 Callback 처리
     */
    public Map<String, Object> loginWithKakao(String code) {
        log.info("📌 카카오 로그인 요청 code: {}", code);

        // 1) AccessToken 요청
        String accessToken = requestAccessToken(code);

        // 2) 사용자 정보 요청
        Map<String, Object> kakaoUserInfo = requestKakaoUserInfo(accessToken);

        Long kakaoId = (Long) kakaoUserInfo.get("id"); // providerId
        String email = (String) kakaoUserInfo.get("email");
        String nickname = (String) kakaoUserInfo.get("nickname");
        String profileImage = (String) kakaoUserInfo.get("profileImage");

        log.info("📌 카카오 사용자 정보 email={}, nickname={}, id={}", email, nickname, kakaoId);

        // 3) DB 조회
        User user = findOrCreateUser(email, kakaoId, nickname, profileImage);

        // 4) JWT 발급
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String jwt = jwtTokenProvider.generateToken(userDetails);

        Map<String, Object> result = new HashMap<>();
        result.put("token", jwt);

        return result;
    }

    // -----------------------------
    // TOKEN 요청
    // -----------------------------
    private String requestAccessToken(String code) {
        String tokenUrl = "https://kauth.kakao.com/oauth/token";

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
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }

    // -----------------------------
    // 사용자 정보 요청
    // -----------------------------
    private Map<String, Object> requestKakaoUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        Map<String, Object> body = response.getBody();
        log.info("📌 카카오 전체 body = {}", body);

        Long id = ((Number) body.get("id")).longValue();

        Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;

        Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        String nickname = profile != null ? (String) profile.get("nickname") : null;
        String profileImage = profile != null ? (String) profile.get("profile_image_url") : null;

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("email", email);
        result.put("nickname", nickname);
        result.put("profileImage", profileImage);

        return result;
    }

    // -----------------------------
    // 사용자 존재 확인 → 없으면 생성
    // -----------------------------
    private User findOrCreateUser(String email, Long kakaoId, String nickname, String profileImage) {

        // 1) 이메일 있는 경우 → 이메일 기준 조회
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email)
                    .orElseGet(() -> createNewSocialUser(email, kakaoId, nickname, profileImage));
        }

        // 2) 이메일 없음 → provider + providerId 조회
        return userRepository.findByProviderAndProviderId("kakao", String.valueOf(kakaoId))
                .orElseGet(() -> createNewSocialUser(null, kakaoId, nickname, profileImage));
    }

    // -----------------------------
    // 신규 유저 생성
    // -----------------------------
    private User createNewSocialUser(String email, Long kakaoId, String nickname, String profileImage) {

        Role defaultRole = roleRepository.findByRole(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

        User newUser = User.createSocialUser(
                email,
                nickname,
                "kakao",
                String.valueOf(kakaoId),
                profileImage,
                defaultRole
        );

        log.info("📌 신규 소셜 유저 생성 → {}", newUser);

        return userRepository.save(newUser);
    }
}
