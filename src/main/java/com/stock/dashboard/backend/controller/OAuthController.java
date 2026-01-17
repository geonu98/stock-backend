package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.exception.EmailRequiredException;
import com.stock.dashboard.backend.model.payload.DeviceInfo;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.service.oauth.SocialLoginService;
import com.stock.dashboard.backend.service.oauth.dto.ConnectEmailRequest;
import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final SocialLoginService socialLoginService;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.logout-redirect-uri}")
    private String kakaoLogoutRedirectUri;

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

        // -----------------------------------------------------------------
        // ✅ 중요
        // - provider 응답에 email이 없다고 해서 "무조건" EMAIL_REQUIRED로 내려버리면 안 된다.
        // - (특히 카카오) email이 아예 내려오지 않는 계정이 많고,
        //   이미 DB에 email 연결 + emailVerified=true 상태일 수도 있다.
        // - 따라서 EMAIL_REQUIRED 여부 판단은 SocialLoginService.loginWithSocialInfo()가
        //   DB 상태(provider+providerId)까지 확인해서 최종 결론을 내리도록 위임한다.
        //
        // - upsertSocialStubIfNeeded는 email 없는 케이스에서 "임시 소셜 유저" row 확보용이므로
        //   여기서 선호출해도 안전하다. (email이 있으면 내부에서 바로 return)
        // -----------------------------------------------------------------
        socialLoginService.upsertSocialStubIfNeeded(userInfo);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setDeviceInfo(req.deviceInfo());

        try {
            JwtAuthenticationResponse tokens =
                    socialLoginService.loginWithSocialInfo(userInfo, loginRequest);

            return ResponseEntity.ok(tokens);

        } catch (EmailRequiredException e) {
            // -----------------------------------------------------------------
            // ✅ EMAIL_REQUIRED
            // - SocialLoginService가 DB 상태까지 확인했는데도 email이 없거나 미인증이면
            //   프론트는 이메일 입력 화면으로 이동해야 한다.
            // - provider/providerId는 connect-email 단계에서 식별자로 사용된다.
            // -----------------------------------------------------------------
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                            "error", "EMAIL_REQUIRED",
                            "provider", e.getProvider(),
                            "providerId", e.getProviderId()
                    )
            );
        }
    }

    /**
     * 소셜 로그인 후 이메일 입력 → 인증 메일 발송
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

    /**
     * 카카오 OAuth 로그아웃 (카카오 세션 종료용 redirect)
     *
     * -----------------------------------------------------------------
     * ✅ 역할
     * - 우리 서비스 로그아웃과는 별개
     * - 카카오 OAuth 세션을 명시적으로 종료하고 싶을 때 사용
     * - 공용 PC / 보안 목적 옵션용
     *
     * ✅ 특징
     * - JWT 인증 불필요 (permitAll)
     * - 서버는 카카오 logout endpoint로 redirect만 수행
     * - 실제 AT/RT 폐기는 프론트에서 /api/auth/logout 호출로 선행 처리
     *
     * 흐름:
     * 프론트
     *   1) POST /api/auth/logout
     *   2) window.location.href = /api/auth/oauth/kakao/logout
     *
     * 카카오
     *   - 세션 종료
     *   - logout_redirect_uri 로 복귀
     * -----------------------------------------------------------------
     */
    @GetMapping("/kakao/logout")
    public void kakaoLogout(HttpServletResponse response) throws IOException {

        String logoutUrl =
                "https://kauth.kakao.com/oauth/logout" +
                        "?client_id=" + kakaoClientId +
                        "&logout_redirect_uri=" + kakaoLogoutRedirectUri;

        response.sendRedirect(logoutUrl);
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
