package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.service.KakaoOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final KakaoOAuthService kakaoOAuthService;


    //카카오 로그인 URL 리다이렉트
    @GetMapping("/kakao") // k 소문자로 변경
    public void redirectToKakaoLogin(HttpServletResponse response) throws IOException {
        String redirectUrl = kakaoOAuthService.getKakaoLoginUrl();
        response.sendRedirect(redirectUrl);
    }
//카카오가 code 전달해주는 곳
    @GetMapping("/kakao/callback")
    public ResponseEntity<?> kakaoCallback(@RequestParam("code") String code) {
        return ResponseEntity.ok(kakaoOAuthService.loginWithKakao(code));
    }
}
