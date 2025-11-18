package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.request.TokenRefreshRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {

        log.info("Login request received: email={}, deviceId={}",
                loginRequest.getEmail(),
                loginRequest.getDeviceInfo() != null ? loginRequest.getDeviceInfo().getDeviceId() : null);

        //  사용자 인증 시도
        Authentication authentication = authService.authenticateUser(loginRequest)
                .orElseThrow(() -> new RuntimeException("로그인 실패"));

        //  인증 객체를 SecurityContext에 등록
        SecurityContextHolder.getContext().setAuthentication(authentication);

        //  사용자 정보 추출
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 변경된 부분: AccessToken + RefreshToken 모두 발급하도록 수정
        JwtAuthenticationResponse tokens = authService.generateTokens(userDetails, loginRequest);

        //  로그인 성공 응답 (Access + Refresh Token 포함)
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        // 5️⃣ 로그아웃 처리
        authService.logout(request);
        return ResponseEntity.ok("Successfully logged out");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestBody TokenRefreshRequest request) {
        log.info("[AuthController] Refresh token 요청 수신");

        String refreshToken = request.getRefreshToken();
        // AuthService로 위임해서 새 AccessToken 발급
        JwtAuthenticationResponse response = authService.refreshAccessToken(refreshToken);


        return ResponseEntity.ok(response);
    }

}
