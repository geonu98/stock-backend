package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.model.payload.DeviceInfo;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.service.oauth.SocialLoginService;
import com.stock.dashboard.backend.service.oauth.dto.ConnectEmailRequest;
import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final SocialLoginService socialLoginService;

    /**
     * OAuth callback
     * - 이메일 있으면 → 바로 로그인
     * - 이메일 없으면 → EMAIL_REQUIRED 반환
     */
    @PostMapping("/{provider}/callback")
    public ResponseEntity<?> socialCallback(
            @PathVariable String provider,
            @RequestBody OAuthCallbackRequest req
    ) {
        SocialUserInfo userInfo =
                socialLoginService.getUserInfo(provider, req.code());

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                            "error", "EMAIL_REQUIRED",
                            "provider", userInfo.provider(),
                            "providerId", userInfo.providerId()
                    )
            );
        }

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setDeviceInfo(req.deviceInfo());

        JwtAuthenticationResponse tokens =
                socialLoginService.loginWithSocialInfo(userInfo, loginRequest);

        return ResponseEntity.ok(tokens);
    }

    /**
     * 이메일 입력 → 인증 메일 발송
     */
    @PostMapping("/connect-email")
    public ResponseEntity<?> connectEmail(
            @RequestBody ConnectEmailWithDeviceRequest req
    ) {
        socialLoginService.connectEmailAndSendVerification(
                req.connectEmailRequest()
        );
        return ResponseEntity.ok().build();
    }

    /* ================= DTO ================= */

    public record OAuthCallbackRequest(
            String code,
            DeviceInfo deviceInfo
    ) {}

    public record ConnectEmailWithDeviceRequest(
            ConnectEmailRequest connectEmailRequest,
            DeviceInfo deviceInfo
    ) {}
}
