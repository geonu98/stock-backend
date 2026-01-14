package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.cache.LoggedOutJwtTokenCache;
import com.stock.dashboard.backend.event.OnUserLogoutSuccessEvent;
import com.stock.dashboard.backend.model.EmailVerificationToken;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.UserDevice;
import com.stock.dashboard.backend.model.payload.request.LogOutRequest;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.model.token.RefreshToken;
import com.stock.dashboard.backend.repository.EmailVerificationTokenRepository;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import com.stock.dashboard.backend.security.JwtTokenValidator;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.util.VerificationTokenCodec;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * ✅ JwtTokenProvider는 "로그인 AccessToken" 발급 및 기존 인증 로직에 사용
     *
     * ⚠️ 주의
     * - 이제는 "이메일 인증용 JWT 토큰 발급(generateEmailVerificationToken)"은 사용하지 않는다.
     * - 즉, JwtTokenProvider는 로그인 토큰 발급용으로만 남긴다.
     */
    private final JwtTokenProvider jwtTokenProvider;

    private final JwtTokenValidator jwtTokenValidator;
    private final LoggedOutJwtTokenCache logoutTokenCache;
    private final RefreshTokenService refreshTokenService;
    private final UserDeviceService userDeviceService;

    /**
     * ✅ 통합된 EmailService
     * - 이메일 인증 메일 발송은 무조건 sendEmailVerification(toEmail, rawToken)만 사용한다.
     */
    private final EmailService emailService;

    /**
     * ✅ DB 기반 이메일 인증 토큰 저장소
     * - rawToken은 저장하지 않는다.
     * - tokenHash만 저장한다.
     */
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    /**
     * ✅ rawToken 생성 + sha256 해시 계산 컴포넌트
     * - 이메일 인증 토큰 생성 규칙을 한 군데로 고정해서 유지보수/일관성 확보
     */
    private final VerificationTokenCodec verificationTokenCodec;

    /**
     * ✅ 이메일 인증 토큰 TTL(분)
     * - 통합 스펙 권장: 15분
     * - application.properties에 없으면 기본값 15 적용
     */
    @Value("${app.email.verify-token-ttl-minutes:15}")
    private long verifyTokenTtlMinutes;

    /**
     * 사용자 인증 메소드
     * @param loginRequest 클라이언트에서 들어온 이메일/비밀번호 정보
     * @return Optional<Authentication> 인증 성공 시 Authentication 객체 반환
     */
    public Optional<Authentication> authenticateUser(LoginRequest loginRequest) {
        log.info("[AuthService] Authenticating user: {}", loginRequest.getEmail());

        // 1) DB에서 사용자 존재 확인
        Optional<User> optionalUser = userRepository.findByEmail(loginRequest.getEmail());
        if (optionalUser.isEmpty()) {
            log.warn("[AuthService] User NOT found in DB: {}", loginRequest.getEmail());
            return Optional.empty();
        } else {
            log.info("[AuthService] User found in DB: {}", loginRequest.getEmail());
        }

        User user = optionalUser.get();

        // 2) 비밀번호 매칭 확인
        boolean matches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        if (!matches) {
            log.warn("[AuthService] Password mismatch for user: {}", loginRequest.getEmail());
            throw new BadCredentialsException("Invalid password");
        }

        // 2.5) ✅ 이메일 인증이 완료되지 않은 계정은 로그인(토큰 발급)을 막는다.
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadCredentialsException("EMAIL_NOT_VERIFIED");
        }

        // 3) Authentication 객체 생성 (Spring Security용)
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        return Optional.of(authentication);
    }


    /**
     * JWT + Refresh Token 생성
     * @param userDetails CustomUserDetails 객체
     * @param loginRequest 로그인 요청(디바이스 정보 포함)
     * @return AccessToken + RefreshToken 응답 DTO
     */
    public JwtAuthenticationResponse generateTokens(CustomUserDetails userDetails, LoginRequest loginRequest) {
        log.info("[AuthService] Generating tokens for user: {}", userDetails.getUsername());

        // 1) Access Token 생성
        String accessToken = jwtTokenProvider.generateToken(userDetails);

        // 2) Refresh Token 생성/갱신
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDevice userDevice = userDeviceService.createOrUpdateDeviceInfo(
                user,
                loginRequest.getDeviceInfo().getDeviceId(),
                loginRequest.getDeviceInfo().getDeviceType()
        );

        Optional<RefreshToken> existingTokenOpt = refreshTokenService.findByUserDevice(userDevice);

        RefreshToken refreshToken;
        if (existingTokenOpt.isPresent()) {
            refreshToken = existingTokenOpt.get();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiryDt(refreshTokenService.generateExpiryDate());
            refreshToken.setUpdatedAt(LocalDateTime.now());
        } else {
            refreshToken = refreshTokenService.createRefreshToken(userDevice);
        }

        refreshTokenService.save(refreshToken);

        return new JwtAuthenticationResponse(
                accessToken,
                refreshToken.getToken(),
                jwtTokenProvider.getJwtExpirationInMs(),
                false,
                user.getId()
        );
    }

    /**
     * JWT 로그아웃 처리
     * - Authorization 헤더에서 토큰 추출
     * - 유효성 검사
     * - Redis 블랙리스트에 등록
     */
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("[Logout] Authorization header is missing or invalid");
            throw new RuntimeException("No JWT token found in request header");
        }

        String token = header.substring(7);

        // 토큰 유효성 검사
        jwtTokenValidator.validateToken(token);

        // 사용자 ID 추출
        Long userId = jwtTokenProvider.getUserIdFromJWT(token);

        // 로그아웃 이벤트 객체 생성
        LogOutRequest logOutRequest = new LogOutRequest();
        logOutRequest.setDeviceInfo(null);

        OnUserLogoutSuccessEvent logoutEvent = new OnUserLogoutSuccessEvent(
                userId.toString(),
                token,
                logOutRequest
        );

        // Redis 캐시에 블랙리스트 등록
        logoutTokenCache.markLogoutEventForToken(logoutEvent);

        log.info("[Logout] Token blacklisted successfully for user {}", userId);
    }

    /**
     * Refresh Token을 이용해 Access Token 재발급
     */
    public JwtAuthenticationResponse refreshAccessToken(String refreshTokenStr) {
        log.info("[AuthService] Refresh Token 기반 Access Token 재발급 시도");

        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenStr)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        refreshTokenService.verifyExpiration(refreshToken);

        UserDevice userDevice = refreshToken.getUserDevice();
        User user = userDevice.getUser();

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        return new JwtAuthenticationResponse(
                newAccessToken,
                refreshToken.getToken(),
                jwtTokenProvider.getJwtExpirationInMs(),
                false,
                user.getId()
        );
    }

    // ---------------------------------------------------------------------
    // ✅ 이메일 인증 메일 재전송 (통합 플로우 버전)
    // ---------------------------------------------------------------------

    /**
     * 이메일 인증 메일 재전송
     *
     * ✅ 통합 이후 정책
     * - JWT 기반 이메일 인증 토큰 발급을 절대 사용하지 않는다.
     * - DB 기반 EmailVerificationToken + verify(/api/auth/email/verify) 플로우만 사용한다.
     *
     * 흐름
     * 1) 이메일로 사용자 조회
     * 2) 이미 emailVerified=true(=enabled)면 재전송 불필요 → 예외
     * 3) rawToken 새로 생성
     * 4) tokenHash 생성 (sha256)
     * 5) EmailVerificationToken DB 저장 (TTL 15분)
     * 6) 통합 EmailService로 메일 발송 (백엔드 verify 링크)
     *
     * @param email 재전송 대상 이메일
     */
    public void resendVerificationEmail(String email) {

        // 1) 사용자 조회
        // - 이메일이 DB에 없으면 재전송할 대상이 없으므로 실패
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("이메일이 존재 하지 않음"));

        // 2) 이미 이메일 인증된 사용자면 재전송 불필요
// - isEnabled()는 "계정 활성(active)" 여부라서 이메일 인증 체크로 쓰면 안 됨
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RuntimeException("이미 인증 완료된 이메일 입니다");
        }
        // 3) rawToken 생성
        // - 이메일 링크에 들어갈 '원문 토큰'
        // - DB에는 절대 저장하지 않는다.
        String rawToken = verificationTokenCodec.newRawToken();

        // 4) tokenHash 생성
        // - DB 저장용 값 (sha256 hex)
        String tokenHash = verificationTokenCodec.sha256Hex(rawToken);

        // 5) DB 토큰 엔티티 생성 + 저장
        // - expiresAt = now + TTL(기본 15분)
        EmailVerificationToken tokenEntity =
                EmailVerificationToken.create(
                        user.getId(),
                        user.getEmail(),
                        tokenHash,
                        Duration.ofMinutes(verifyTokenTtlMinutes)
                );

        emailVerificationTokenRepository.save(tokenEntity);

        // 6) 인증 메일 발송
        // - 링크는 항상 백엔드 verify API로만 만든다.
        //   GET {BACKEND}/api/auth/email/verify?token={rawToken}
        emailService.sendEmailVerification(user.getEmail(), rawToken);

        log.info("[AuthService] Verification email resent: email={}, ttlMinutes={}",
                user.getEmail(), verifyTokenTtlMinutes);
    }
}
