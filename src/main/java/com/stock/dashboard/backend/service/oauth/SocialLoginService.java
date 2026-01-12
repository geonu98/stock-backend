package com.stock.dashboard.backend.service.oauth;

import com.stock.dashboard.backend.exception.ResourceAlreadyInUseException;
import com.stock.dashboard.backend.model.EmailVerificationToken;
import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.RoleName;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.repository.EmailVerificationTokenRepository;
import com.stock.dashboard.backend.repository.RoleRepository;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.service.AuthService;
import com.stock.dashboard.backend.service.EmailService;
import com.stock.dashboard.backend.service.oauth.dto.ConnectEmailRequest;
import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * SocialLoginService
 *
 * ✅ 역할: 소셜 로그인 전체 흐름의 "중앙 조정자(Orchestrator)"
 * - OAuth Provider 선택
 * - 소셜 사용자 정보 조회/회원 생성/연결
 * - 이메일이 없는 소셜 계정(EMAIL_REQUIRED) 처리: 이메일 입력 → 인증메일 발송까지
 * - 최종 로그인 완료 시 일반 로그인과 동일한 방식으로 AT(JWT) + RT(UUID) 발급
 *
 * ✅ 이번 리팩토링 핵심
 * --------------------------------------------------------------------
 * 1) "메일 발송/링크 생성" 책임은 EmailService로 완전히 위임한다.
 *    - SocialLoginService가 프론트/백엔드 URL을 섞어서 링크를 만들면 운영에서 반드시 터진다.
 *    - EmailService가 'backend verify 링크'를 만들고, verify 성공 후 redirect는 EmailVerificationService가 담당.
 *
 * 2) connect-email 단계에서는 절대 user.email 저장/토큰 발급을 하지 않는다.
 *    - connect-email은 "로그인"이 아니라, "이메일 소유 확인 프로세스 시작" 단계다.
 *
 * ✅ 올바른 EMAIL_REQUIRED 흐름(소셜 이메일 없음 케이스)
 * --------------------------------------------------------------------
 * (1) 프론트: /email-required에서 이메일 입력
 * (2) POST /api/auth/oauth/connect-email
 * (3) 서버: EmailVerificationToken 생성/저장 + EmailService로 인증메일 발송
 * (4) 사용자 메일 클릭:
 *     -> GET {backend}/api/auth/email/verify?token=...
 * (5) 서버: verify 처리 + 1회용 code 발급(Redis) + 프론트로 redirect:
 *     -> {frontend}/email-verified?code=...
 * (6) 프론트: /email-verified 페이지에서 POST /api/auth/email/exchange
 * (7) 서버: code 교환 → AT/RT 발급(RefreshToken은 UserDevice와 연계)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLoginService {

    /**
     * 등록된 모든 SocialOAuthService 구현체 목록
     * (KakaoOAuthService, GoogleOAuthService 등)
     */
    private final List<SocialOAuthService> socialOAuthServices;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    /**
     * 일반 로그인과 동일한 토큰 발급 로직을 재사용하기 위해 AuthService 주입
     */
    private final AuthService authService;

    /**
     * ✅ 인증메일 발송 담당
     * - "메일 링크 생성(backend verify URL)"까지 EmailService가 책임진다.
     */
    private final EmailService emailService;

    /**
     * 프론트엔드 주소
     * - (여기서는) OAuth redirect_uri 생성에만 사용
     * - ⚠️ 인증메일 링크 생성에는 절대 사용하지 않는다.
     */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * ✅ 소셜 로그인 시작
     *
     * 프론트 → GET /api/auth/oauth/{provider}
     * 백엔드는 provider에 맞는 OAuthService를 찾아 authorize URL을 만들어 반환/redirect한다.
     *
     * ⚠ redirect_uri는 OAuth 콘솔 등록값과 100% 동일해야 한다.
     * - 카카오: redirect_uri에 쿼리스트링 붙이면 KOE006 에러 가능
     */
    public String getLoginUrl(String provider) {
        SocialOAuthService oAuthService = socialOAuthServices.stream()
                .filter(service -> service.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + provider));

        // 현재 구조: 프론트 공통 콜백(/oauth/callback)로 code를 받는 설계
        String redirectUri = frontendUrl + "/oauth/callback";

        return oAuthService.getAuthorizeUrl(redirectUri);
    }

    /**
     * ✅ OAuth provider로부터 사용자 정보 조회
     *
     * - authorization code를 이용해 access token 요청 후 사용자 정보를 받아온다.
     * - 이 메서드는 OAuth 통신만 담당하고, DB/토큰은 다른 메서드에서 처리한다.
     */
    public SocialUserInfo getUserInfo(String provider, String code) {
        SocialOAuthService oAuthService = socialOAuthServices.stream()
                .filter(service -> service.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + provider));

        return oAuthService.getUserInfo(code);
    }

    /**
     * ✅ (이메일이 있는) 소셜 사용자 로그인 처리
     *
     * - 소셜 계정 정보 기반으로 기존 사용자 조회 or 신규 사용자 생성
     * - 일반 로그인과 동일하게 Access + Refresh Token 발급
     */
    public JwtAuthenticationResponse loginWithSocialInfo(
            SocialUserInfo info,
            LoginRequest loginRequest
    ) {
        // 소셜 provider가 이메일을 제공하지 않은 경우 -> 프론트에 EMAIL_REQUIRED로 보내는 구조가 필요
        if (info.email() == null || info.email().isBlank()) {
            throw new IllegalStateException("Email required for social login");
        }

        return processSocialLogin(
                info.email(),
                info.provider(),
                info.providerId(),
                info,
                loginRequest
        );
    }

    /**
     * ✅ 이메일 없는 소셜 유저 → 사용자가 이메일 입력 후 호출되는 단계
     *
     * POST /api/auth/oauth/connect-email
     *
     * ✅ 이 단계에서 하는 일
     * 1) provider + providerId 로 "임시 소셜 유저" 조회
     * 2) 이메일 중복 검사(다른 유저가 이미 사용 중이면 409)
     * 3) EmailVerificationToken 생성/저장
     * 4) EmailService로 인증메일 발송 (메일 링크는 백엔드 verify API를 가리킴)
     *
     * ❌ 이 단계에서 하면 안 되는 일
     * - user.email 저장 ❌ (verify 성공 시점에 저장)
     * - emailVerified=true 처리 ❌ (verify 성공 시점에 처리)
     * - AccessToken/RefreshToken 발급 ❌ (exchange 단계에서 발급)
     */
    public void connectEmailAndSendVerification(ConnectEmailRequest req) {

        // 0️⃣ 입력 검증
        if (req.email() == null || req.email().isBlank()) {
            // 400으로 내리고 싶으면 BadRequestException 추천
            throw new IllegalArgumentException("Email required");
        }

        // 1️⃣ provider + providerId로 기존 "임시 소셜 유저" 찾기
        // - 이미 OAuth callback 단계에서 provider/providerId 기반으로 user row를 만들었거나,
        //   최소한 identify 가능한 상태여야 한다.
        User user = userRepository.findByProviderAndProviderId(
                req.provider(),
                req.providerId()
        ).orElseThrow(() -> new IllegalStateException("Social user not found"));

        // 2️⃣ 이메일 중복 체크
        // - "이미 다른 사용자에게 연결된 이메일"이면 막는다.
        // - 같은 유저면 통과 (혹시 이미 저장되어 있는 케이스 대비)
        userRepository.findByEmail(req.email())
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    // ✅ 409 Conflict
                    throw new ResourceAlreadyInUseException("User", "email", req.email());
                });

        // 3️⃣ raw token 생성 (메일에 그대로 들어가는 값)
        // - DB에는 hash(tokenHash)로 저장되고, raw는 저장하지 않는다.
        String rawToken = UUID.randomUUID().toString();

        // 4️⃣ EmailVerificationToken 생성/저장
        // - 엔티티에서 tokenHash 생성 + expiresAt 세팅 + used=false 등을 처리
        EmailVerificationToken token =
                EmailVerificationToken.create(user, req.email(), rawToken);

        emailVerificationTokenRepository.save(token);

        // 5️⃣ ✅ 인증메일 발송은 EmailService로 위임 (단일 책임)
        // - EmailService 내부에서 "backend verify 링크" 생성:
        //   GET {backend}/api/auth/email/verify?token={rawToken}
        // - verify 성공 시 redirect는 EmailVerificationService가 프론트로 수행
        emailService.sendSocialConnectEmailVerification(req.email(), rawToken);

        log.info("[SOCIAL CONNECT-EMAIL] verification mail requested provider={}, providerId={}, email={}",
                req.provider(), req.providerId(), req.email());
    }

    /**
     * ✅ 소셜 로그인 핵심 처리 메서드 (공통 로직)
     *
     * ✔ 사용자 조회 / 생성
     * ✔ 소셜 계정 연결
     * ✔ 프로필 정보 업데이트
     * ✔ 일반 로그인과 동일한 토큰 발급
     *
     * ⚠ 이 메서드는 "로그인 완료" 단계만 담당
     */
    private JwtAuthenticationResponse processSocialLogin(
            String email,
            String provider,
            String providerId,
            SocialUserInfo info,
            LoginRequest loginRequest
    ) {
        // 이메일 기준 사용자 조회 or 신규 생성
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewSocialUser(info));

        // provider + providerId 연결
        user.connectSocial(provider, providerId);

        // 최신 프로필 정보 동기화
        if (info.nickname() != null && !info.nickname().isBlank()) {
            user.updateNickname(info.nickname());
        }
        if (info.profileImage() != null) {
            user.updateProfileImage(info.profileImage());
        }

        userRepository.save(user);

        // 일반 로그인(AuthService)과 동일한 방식으로 토큰 발급
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return authService.generateTokens(userDetails, loginRequest);
    }

    /**
     * ✅ 신규 소셜 사용자 생성
     *
     * - 기본 권한: ROLE_USER
     * - 소셜 전용 사용자 생성 팩토리 메서드 사용
     */
    private User createNewSocialUser(SocialUserInfo info) {
        Role defaultRole = roleRepository.findByRole(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

        return userRepository.save(
                User.createSocialUser(
                        info.email(),
                        info.nickname(),
                        info.provider(),
                        info.providerId(),
                        info.profileImage(),
                        defaultRole
                )
        );
    }
}
