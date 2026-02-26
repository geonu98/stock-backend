package com.stock.dashboard.backend.service.oauth;

import com.stock.dashboard.backend.exception.EmailRequiredException;
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
import com.stock.dashboard.backend.service.UserService;
import com.stock.dashboard.backend.service.oauth.dto.ConnectEmailRequest;
import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import com.stock.dashboard.backend.util.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * SocialLoginService
 * - OAuth Provider 선택
 * - 소셜 사용자 정보 조회/회원 생성/연결
 * - 이메일이 없는 소셜 계정(EMAIL_REQUIRED) 처리: 이메일 입력 → 인증메일 발송까지
 * - 최종 로그인 완료 시 일반 로그인과 동일한 방식으로 AT(JWT) + RT(UUID) 발급
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

    private final List<SocialOAuthService> socialOAuthServices;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    private final AuthService authService;

    /**
     *  인증메일 발송 담당
     * - "메일 링크 생성(backend verify URL)"까지 EmailService가 책임진다.
     */
    private final EmailService emailService;

    /**
     *  nickname 정책(유니크 생성)을 UserService로 위임
     * - 소셜에서 받아온 nickname/displayName은 "seed"로만 사용한다.
     * - DB에 저장되는 nickname은 항상 유니크하게 생성되며, 사용자가 바꾼 값이 있으면 유지한다.
     */
    private final UserService userService;

    @Value("${app.email.verify-token-ttl-minutes:30}")
    private long emailVerifyTokenTtlMinutes;

    private final VerificationTokenCodec verificationTokenCodec;

    /**
     * 프론트엔드 주소
     * - (여기서는) OAuth redirect_uri 생성에만 사용
     * -  인증메일 링크 생성에는 절대 사용하지 않는다.
     */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;


    /**
     *  소셜 로그인 시작
     *
     * 프론트 → GET /api/auth/oauth/{provider}
     * 백엔드는 provider에 맞는 OAuthService를 찾아 authorize URL을 만들어 반환/redirect한다.
     *
     *  redirect_uri는 OAuth 콘솔 등록값과 100% 동일해야 한다.
     * - 카카오: redirect_uri에 쿼리스트링 붙이면 KOE006 에러 가능
     */
    public String getLoginUrl(String provider) {
        SocialOAuthService oAuthService = socialOAuthServices.stream()
                .filter(service -> service.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + provider));

        // 현재 구조: 프론트 공통 콜백(/oauth/callback)로 code를 받는 설계
        //변경 카카오 redirect_uri 단일화
        String redirectUri = kakaoRedirectUri;

        return oAuthService.getAuthorizeUrl(redirectUri);
    }

    /**
     *  OAuth provider로부터 사용자 정보 조회
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
     *  (이메일이 있는) 소셜 사용자 로그인 처리
     *
     * - 소셜 계정 정보 기반으로 기존 사용자 조회 or 신규 사용자 생성
     * - 일반 로그인과 동일하게 Access + Refresh Token 발급
     */
    public JwtAuthenticationResponse loginWithSocialInfo(
            SocialUserInfo info,
            LoginRequest loginRequest
    ) {
        // 소셜 provider가 이메일을 제공하지 않은 경우 -> 프론트에 EMAIL_REQUIRED로 보내는 구조가 필요
        // -----------------------------------------------------------------
        //  추가 정책(중요)
        // - 카카오는 동의/스코프/계정 설정에 따라 email을 아예 내려주지 않을 수 있다.
        // - 하지만 "이미 DB에 이메일이 연결 + emailVerified=true"인 유저라면,
        //   provider 응답 email이 null이어도 바로 로그인 처리되어야 한다.
        // - 따라서 "provider 응답 email"이 없을 때는
        //   provider+providerId로 DB 유저를 조회하여 DB 상태로 판단한다.
        // -----------------------------------------------------------------
        if (info.email() == null || info.email().isBlank()) {

            User existing = userRepository.findByProviderAndProviderId(info.provider(), info.providerId())
                    .orElse(null);

            //  DB에 이미 이메일이 연결되어 있고 인증까지 끝났으면 정상 로그인 진행
            if (existing != null
                    && existing.getEmail() != null && !existing.getEmail().isBlank()
                    && Boolean.TRUE.equals(existing.getEmailVerified())) {

                return processSocialLogin(
                        existing.getEmail(),
                        info.provider(),
                        info.providerId(),
                        info,
                        loginRequest
                );
            }

            //
            throw new EmailRequiredException(info.provider(), info.providerId());
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
     *  이메일 없는 소셜 유저 → 사용자가 이메일 입력 후 호출되는 단계
     *
     * POST /api/auth/oauth/connect-email
     *
     *  이 단계에서 하는 일
     * 1) provider + providerId 로 "임시 소셜 유저" 조회
     * 2) 이메일 중복 검사(다른 유저가 이미 사용 중이면 409)
     * 3) EmailVerificationToken 생성/저장
     * 4) EmailService로 인증메일 발송 (메일 링크는 백엔드 verify API를 가리킴)
     *
     *  이 단계에서 하면 안 되는 일
     * - user.email 저장 ❌ (verify 성공 시점에 저장)
     * - emailVerified=true 처리 ❌ (verify 성공 시점에 처리)
     * - AccessToken/RefreshToken 발급 ❌ (exchange 단계에서 발급)
     */
    public void connectEmailAndSendVerification(ConnectEmailRequest req) {

        //  입력 검증
        if (req.email() == null || req.email().isBlank()) {
            // 400으로 내리고 싶으면 BadRequestException 추천
            throw new IllegalArgumentException("Email required");
        }

        //  provider + providerId로 기존 "임시 소셜 유저" 찾기
        // - 이미 OAuth callback 단계에서 provider/providerId 기반으로 user row를 만들었거나,
        //   최소한 identify 가능한 상태여야 한다.
        User user = userRepository.findByProviderAndProviderId(
                req.provider(),
                req.providerId()
        ).orElseThrow(() -> new IllegalStateException("Social user not found"));

        // 이메일 중복 체크
        // - "이미 다른 사용자에게 연결된 이메일"이면 막는다.
        // - 같은 유저면 통과 (혹시 이미 저장되어 있는 케이스 대비)
        userRepository.findByEmail(req.email())
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    //  409 Conflict
                    throw new ResourceAlreadyInUseException("User", "email", req.email());
                });

        //  raw token 생성 (메일에 들어가는 값)
        String rawToken = verificationTokenCodec.newRawToken();

        //  DB에는 hash만 저장
        String tokenHash = verificationTokenCodec.sha256Hex(rawToken);

        Duration ttl = Duration.ofMinutes(emailVerifyTokenTtlMinutes); // 설정값 권장

        EmailVerificationToken token =
                EmailVerificationToken.create(user, req.email(), tokenHash, ttl);

        emailVerificationTokenRepository.save(token);

        //  메일에는 raw를 보냄
        emailService.sendEmailVerification(req.email(), rawToken);

        log.info("[SOCIAL CONNECT-EMAIL] verification mail requested provider={}, providerId={}, email={}",
                req.provider(), req.providerId(), req.email());
    }

    /**
     *  소셜 로그인 핵심 처리 메서드 (공통 로직)
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
        // -----------------------------------------------------------------
        //  추가 정책(안정성)
        // - 소셜 로그인에서 "진짜 식별자"는 provider+providerId다.
        // - provider 응답 email은 null일 수 있고, 사용자가 이메일을 바꾸는 정책도 있을 수 있다.
        // - 따라서 provider+providerId 우선 조회 → (있다면) 그 유저를 사용하고,
        //   없을 때만 email 기반 조회/생성을 진행한다.
        // -----------------------------------------------------------------
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .orElseGet(() -> createNewSocialUser(info)));

        // provider + providerId 연결
        user.connectSocial(provider, providerId);

        // 최신 프로필 정보 동기화
        //  정책: 소셜에서 받은 nickname(displayName)을 DB nickname으로 직접 저장/덮어쓰기 하지 않는다.
        // - nickname은 항상 유니크 생성(또는 사용자가 바꾼 값 유지)
        // - 소셜 nickname은 seed로만 사용
        if (info.profileImage() != null && !info.profileImage().isBlank()) {
            user.updateProfileImage(info.profileImage());
        }

        //  저장 직전 nickname 보정(비어있을 때만 유니크 생성)
        userService.ensureNicknameBeforeSave(user, info.nickname());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            //  nickname UNIQUE 충돌(레이스) 대비: seed 기반으로 강제 재생성 후 1회 재시도
            userService.forceRegenerateNickname(user, info.nickname());
            userRepository.save(user);
        }

        // 일반 로그인(AuthService)과 동일한 방식으로 토큰 발급
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return authService.generateTokens(userDetails, loginRequest);
    }

    @Transactional
    public void upsertSocialStubIfNeeded(SocialUserInfo info) {
        // 이메일이 있으면 stub 필요 없음
        if (info.email() != null && !info.email().isBlank()) {
            return;
        }

        User user = userRepository.findByProviderAndProviderId(info.provider(), info.providerId())
                .orElseGet(() -> {
                    Role defaultRole = roleRepository.findByRole(RoleName.ROLE_USER)
                            .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

                    //  정책: 소셜 nickname을 DB nickname에 직접 저장하지 않음 (seed로만 사용)
                    // - stub도 nickname은 최종적으로 ensureNicknameBeforeSave에서 채워짐
                    return User.createSocialStub(
                            info.provider(),
                            info.providerId(),
                            null,
                            info.profileImage(),
                            defaultRole
                    );
                });

        // 기존 stub이면 최신 프로필만 동기화
        //  nickname은 덮어쓰지 않음 (seed로만 사용)
        if (info.profileImage() != null && !info.profileImage().isBlank()) {
            user.updateProfileImage(info.profileImage());
        }

        //  저장 직전 nickname 보정(비어있을 때만 유니크 생성)
        userService.ensureNicknameBeforeSave(user, info.nickname());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            //  nickname UNIQUE 충돌(레이스) 대비: 강제 재생성 후 1회 재시도
            userService.forceRegenerateNickname(user, info.nickname());
            userRepository.save(user);
        }
    }

    /**
     *  신규 소셜 사용자 생성
     *
     * - 기본 권한: ROLE_USER
     * - 소셜 전용 사용자 생성 팩토리 메서드 사용
     */
    private User createNewSocialUser(SocialUserInfo info) {
        Role defaultRole = roleRepository.findByRole(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

        //  정책: 소셜 nickname을 DB nickname에 직접 저장하지 않음 (seed로만 사용)
        // - 실제 nickname은 processSocialLogin에서 저장 직전 ensureNicknameBeforeSave로 채움
        return userRepository.save(
                User.createSocialUser(
                        info.email(),
                        null,
                        info.provider(),
                        info.providerId(),
                        info.profileImage(),
                        defaultRole
                )
        );
    }
}
