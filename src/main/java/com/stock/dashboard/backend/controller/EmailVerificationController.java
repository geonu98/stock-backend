package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.model.payload.DeviceInfo;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * EmailVerificationController
 *
 * 목적
 * - 소셜 계정에 이메일이 없는 케이스(EMAIL_REQUIRED)를 처리하기 위한 "이메일 인증 완료 → 최종 로그인" 흐름을 담당한다.
 *
 *  이 컨트롤러가 해결하는 문제
 * - connect-email 단계에서는 토큰을 발급하지 않는다. (로그인 아님)
 * - 사용자가 이메일 링크를 클릭해 "이메일 소유"를 증명한 뒤,
 * - 프론트가 1회용 code를 받아서 최종 토큰(AT/RT)을 교환(exchange)한다.
 *
 *  핵심 원칙
 * 1) 토큰(AT/RT)을 URL로 절대 전달하지 않는다.
 * 2) URL에는 오직 "짧은 TTL의 1회용 code"만 포함한다.
 * 3) deviceInfo는 이메일 링크(verify)에서 받지 않고, 최종 교환(exchange) 단계에서만 받는다.
 *    - 이유: 이메일은 다양한 기기/앱에서 열릴 수 있고, 로그인할 "실제 브라우저"는 프론트에서 확정하는 게 안전하다.
 */
@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     *  1) 이메일 인증 링크 클릭 처리 (verify)
     *
     * 이메일 링크 예시:
     * GET /api/auth/email/verify?token=xxxx
     *
     * 흐름(서버):
     * 1) rawToken -> hash -> DB 조회(EmailVerificationToken)(만료/위조/이미 사용됨 등)
     * 성공 시에만 Redis code 발급
     * 2) 인증 성공하면 "1회용 code"를 새로 발급한다. (짧은 TTL, 예: 5분)
     * 3) 발급한 code를 Redis에 저장한다. (code → provider/providerId/email 등 payload 매핑)
     * 4) 프론트엔드 URL로 redirect 한다.
     *    예: http://localhost:5173/email-verified?code=xxxx
     *
     *  주의
     * - 여기서 AccessToken/RefreshToken을 발급하면 안 된다.
     * - 이메일 링크를 누른 환경이 곧 로그인할 환경이라는 보장이 없다.
     * - 그러므로 최종 토큰 발급은 프론트가 code를 들고 POST로 교환(exchange)할 때 수행한다.
     */
    @GetMapping("/verify")
    public void verifyEmail(
            @RequestParam("token") String token,
            HttpServletResponse response
    ) throws IOException {

        // 인증 성공 시 프론트로 redirect 할 URL을 서비스에서 생성하여 반환
        // 예: http://localhost:5173/email-verified?code=...
        String redirectUrl = emailVerificationService.verifyEmail(token);

        response.sendRedirect(redirectUrl);
    }

    /**
     *  2) 프론트에서 1회용 code를 이용해 최종 로그인 토큰을 교환 (exchange)
     *
     * 프론트가 도착하는 페이지:
     * - /email-verified?code=xxxx
     *
     * 프론트가 호출하는 API:
     * POST /api/auth/email/exchange
     *
     * body 예시:
     * {
     *   "code": "1회용 코드",
     *   "deviceInfo": { "deviceId": "...", "deviceType": "WEB" }
     * }
     *
     * 흐름(서버):
     * 1) Redis에서 code를 찾는다. (없으면 만료/잘못된 code)
     * 2) code는 1회용으로 "소모"한다. (재사용 방지)
     * 3) payload에 들어있는 (provider/providerId/email)를 기반으로 사용자 확정 처리:
     *    - user.email 연결
     *    - emailVerified = true
     *    - 필요한 경우 신규 유저 생성/업데이트
     * 4) AccessToken(JWT) + RefreshToken(UUID opaque) 발급
     *    - RefreshToken은 반드시 DB에 저장하고 deviceId와 연계한다. (UserDevice/RefreshToken 테이블)
     *
     *  결과:
     * - JwtAuthenticationResponse (accessToken, refreshToken 등)
     */
    @PostMapping("/exchange")
    public ResponseEntity<JwtAuthenticationResponse> exchange(
            @RequestBody EmailExchangeRequest req
    ) {
        // 기존 코드 구조를 최대한 유지하기 위해 LoginRequest를 재사용
        // (하지만 의미적으로는 "로그인"이 아니라 "이메일 인증 후 토큰 교환(exchange)"임)
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setDeviceInfo(req.deviceInfo());

        JwtAuthenticationResponse tokens =
                emailVerificationService.exchangeCode(req.code(), loginRequest);

        return ResponseEntity.ok(tokens);
    }

    /**
     * ✅ exchange 요청 DTO
     *
     * - code: verify 단계에서 서버가 생성해 프론트로 redirect 하며 전달한 1회용 code
     * - deviceInfo: refreshToken 발급 및 디바이스 바인딩을 위해 필요
     *
     * ⚠️ 주의
     * - deviceInfo는 verify 링크(메일 클릭)에서 받지 않는다.
     * - exchange 요청에서만 받는다.
     */
    public static record EmailExchangeRequest(
            String code,
            DeviceInfo deviceInfo
    ) {}
}
