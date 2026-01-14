package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.payload.request.*;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.service.AuthService;
import com.stock.dashboard.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController
 *
 *  담당 범위
 * - 회원가입(signup): 계정 생성 + 이메일 인증 메일 발송(로그인 아님)
 * - 로그인(login): ID/PW 로그인 토큰 발급
 * - 로그아웃/리프레시: 기존 세션 관리
 * - 이메일 재전송(resend): "DB 기반 이메일 인증 토큰" 재발급 + 메일 재발송
 *
 *  통합 이후 중요한 원칙
 * - 이메일 인증 링크 클릭(verify)은 더 이상 여기서 처리하지 않는다.
 * - 이메일 verify는 반드시 아래 컨트롤러에서 처리한다:
 *   - EmailVerificationController: /api/auth/email/verify
 *   - EmailVerificationController: /api/auth/email/exchange
 *
 * 즉,
 * - /api/auth/verify-email (JWT 기반)  삭제 완료
 * - /api/auth/email/verify (DB 토큰 기반)  단일 진입점
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    // ---------------------------------------------------------------------
    //  1) 회원가입 (로컬)
    // ---------------------------------------------------------------------

    /**
     * 회원가입
     *
     *  동작
     * 1) User 생성(emailVerified=false)
     * 2) EmailVerificationToken(DB) 생성/저장 (tokenHash만 저장)
     * 3) 인증 메일 발송 (백엔드 verify 링크로)
     *
     *  주의
     * - 여기서는 로그인(AT/RT 발급)을 하지 않는다.
     * - 최종 로그인 처리는 "메일 클릭 verify → 프론트 code 수신 → exchange"에서 완료된다.
     */
    @PostMapping("/signup")
    public ResponseEntity<?> regiserUser(@Valid @RequestBody SignUpRequest request) {
        log.info("[AuthController] Signup request received: email={}", request.getEmail());

        User savedUser = userService.registerUser(request);

        // 응답은 "가입 완료"보다는 "메일 보냈음"이 플로우 설명에 더 정확함
        // (프론트에서 이 문구로 UX 구성 가능)
        return ResponseEntity.ok("회원가입 요청 완료: 이메일 인증 메일을 확인해주세요.");
    }

    // ---------------------------------------------------------------------
    //  2) 로그인 (로컬)
    // ---------------------------------------------------------------------

    /**
     * 로그인
     *
     *  여기서는 "기존 로그인 로직" 그대로 사용
     * - ID/PW 인증 성공 시 AccessToken + RefreshToken 발급
     *
     *  정책에 따라 emailVerified=false 유저 로그인 제한을 걸고 싶다면
     * - AuthService.authenticateUser() 또는 인증 필터에서 막아야 함
     * - (현재는 너 프로젝트 정책을 몰라서 여기서는 변경하지 않음)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {

        log.info("[AuthController] Login request received: email={}, deviceId={}",
                loginRequest.getEmail(),
                loginRequest.getDeviceInfo() != null ? loginRequest.getDeviceInfo().getDeviceId() : null);

        // 1) 사용자 인증 시도 (성공하면 Authentication 반환)
        Authentication authentication = authService.authenticateUser(loginRequest)
                .orElseThrow(() -> new RuntimeException("로그인 실패"));

        // 2) 인증 객체를 SecurityContext에 등록
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3) 사용자 정보 추출
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 4) AccessToken + RefreshToken 발급
        JwtAuthenticationResponse tokens = authService.generateTokens(userDetails, loginRequest);

        return ResponseEntity.ok(tokens);
    }

    // ---------------------------------------------------------------------
    //  3) 로그아웃 / 리프레시
    // ---------------------------------------------------------------------

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok("Successfully logged out");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestBody TokenRefreshRequest request) {
        log.info("[AuthController] Refresh token 요청 수신");

        JwtAuthenticationResponse response = authService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------------
    //  4) 이메일 인증 메일 재전송
    // ---------------------------------------------------------------------

    /**
     * 이메일 인증 메일 재전송
     *
     *  통합 이후 동작
     * - JWT 이메일 토큰 생성
     * - DB EmailVerificationToken 생성/저장
     * - sendEmailVerification(toEmail, rawToken) 단일 메서드로 메일 발송
     *
     * ️ 사용자가 받은 메일 링크는 항상 아래로 들어감:
     * - GET /api/auth/email/verify?token=...
     */
    @PostMapping("/resend-verification-email")
    public ResponseEntity<?> resendVerificationEmail(@RequestBody EmailResendRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok("이메일 인증 메일을 다시 보냈습니다.");
    }

    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@Valid @RequestBody EmailCheckRequest req) {

        boolean available = !userService.existsByEmail(req.getEmail().trim());

        return ResponseEntity.ok(new EmailCheckResponse(available));
    }

    @Data
    public static class EmailCheckRequest {
        @NotBlank
        @Email
        private String email;
    }

    public record EmailCheckResponse(boolean available) {}
}
