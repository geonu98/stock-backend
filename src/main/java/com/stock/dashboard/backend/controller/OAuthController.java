package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.service.oauth.KakaoOAuthService;
import com.stock.dashboard.backend.service.oauth.SocialLoginService;
import com.stock.dashboard.backend.service.oauth.dto.ConnectEmailRequest;
import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {


    private final SocialLoginService socialLoginService;


    //카카오 로그인 URL 리다이렉트
    @GetMapping("/{provider}")
    public void redirectToSocialLogin(
            @PathVariable("provider") String provider,
            HttpServletResponse response
    ) throws IOException {
        String loginUrl = socialLoginService.getLoginUrl(provider);
        response.sendRedirect(loginUrl);
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<?> socialCallback(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code
    ) {
        // 1️⃣ 우선 Provider에서 사용자 정보 확보
        SocialUserInfo userInfo = socialLoginService.getUserInfo(provider, code);

        // 2️⃣ 이메일 없는 경우 → 로그인 중단 + 이메일 입력 페이지로 리다이렉트
        if (userInfo.email() == null || userInfo.email().isBlank()) {
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .header("Location",
                            "/email-required?provider=" + userInfo.provider() +
                                    "&providerId=" + userInfo.providerId()
                    )
                    .build();
        }

        // 3️⃣ 이메일 있는 경우 → 기존 로그인 로직 수행
        return ResponseEntity.ok(
                socialLoginService.loginWithSocialInfo(userInfo)
        );
    }


    @PostMapping("/connect-email")
    public ResponseEntity<?> connectEmail(
            @RequestBody ConnectEmailRequest req
    ) {
        // 로그인/가입 처리 후 JWT 발급
        String token = socialLoginService.connectEmailAndLogin(req);
        return ResponseEntity.ok(token);
    }


}
