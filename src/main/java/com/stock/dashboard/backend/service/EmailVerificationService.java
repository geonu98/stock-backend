package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.exception.BadRequestException;
import com.stock.dashboard.backend.exception.ExpiredTokenException;
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
import com.stock.dashboard.backend.util.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * EmailVerificationService (DB 토큰 기반 통합 버전)
 *
 * ✅ 역할
 * 1) verify 단계(메일 클릭):
 *    - rawToken 검증
 *    - EmailVerificationToken(DB) 조회 (hash로)
 *    - 만료/사용 여부 체크
 *    - User에 email 연결 + emailVerified=true 처리
 *    - token.usedAt 기록(1회용)
 *    - Redis에 1회용 code 저장
 *    - 프론트 redirect URL 반환 (/email-verified?code=...)
 *
 * 2) exchange 단계(프론트 POST):
 *    - Redis에서 code 조회
 *    - code 1회용 소모(삭제)
 *    - userId로 유저 조회
 *    - emailVerified 확인
 *    - deviceInfo 확인
 *    - 최종 AT/RT 발급
 *
 * ✅ 상태코드 규칙 (프론트 UX 분기용)
 * - 400: token/code 누락·위조·요청형식 오류
 * - 409: 이미 사용됨(토큰 재사용, code 중복 소모 등), 이메일 충돌
 * - 410: 만료됨(token 만료, code 만료/미존재)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    /**
     * Redis key prefix
     * - value에는 userId만 저장한다.
     *
     * 예)
     *  email:verify:code:{code} -> "123"
     */
    private static final String REDIS_KEY_PREFIX = "email:verify:code:";

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * 최종 로그인 토큰(AT/RT) 발급은 AuthService가 담당한다.
     * - verify 단계에서는 절대 AT/RT 발급하지 않는다.
     * - exchange 단계에서만 발급한다.
     */
    private final AuthService authService;

    /**
     * 1회용 code 저장소 (Redis)
     * - verify 단계: code -> userId 저장
     * - exchange 단계: code 조회 후 즉시 delete (1회용)
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * ✅ rawToken 생성/해시 계산 전용 컴포넌트
     * - "토큰 해시 규칙"을 여기로 고정한다.
     * - 엔티티(EmailVerificationToken)에는 hash 로직을 두지 않는다.
     */
    private final VerificationTokenCodec verificationTokenCodec;

    /**
     * 프론트엔드 주소
     * - verify 성공 후 여기로 redirect 한다.
     * 예) http://localhost:5173
     */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * ✅ 1) 이메일 인증 링크 클릭 처리 (verify)
     */
    @Transactional
    public String verifyEmail(String rawToken) {

        // -----------------------------------------------------------------
        // 0) 입력 검증: token 누락이면 400
        // -----------------------------------------------------------------
        if (!StringUtils.hasText(rawToken)) {
            throw new BadRequestException("Token is required");
        }

        // -----------------------------------------------------------------
        // 1) rawToken -> tokenHash
        // -----------------------------------------------------------------
        String tokenHash = verificationTokenCodec.sha256Hex(rawToken);

        // -----------------------------------------------------------------
        // 2) DB 조회
        // -----------------------------------------------------------------
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid email verification token"));

        // -----------------------------------------------------------------
        // 3) 만료/사용 여부 체크
        // -----------------------------------------------------------------
        if (token.isExpired()) {
            throw new ExpiredTokenException("Email verification token expired");
        }
        if (token.isUsed()) {
            throw new ResourceAlreadyInUseException("EmailVerificationToken", "token", "already used");
        }

        // -----------------------------------------------------------------
        // 4) 사용자 조회
        // -----------------------------------------------------------------
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String emailToConnect = token.getEmail();

        // -----------------------------------------------------------------
        // 5) 이메일 중복 체크
        // -----------------------------------------------------------------
        userRepository.findByEmail(emailToConnect)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ResourceAlreadyInUseException("User", "email", emailToConnect);
                });

        // -----------------------------------------------------------------
        // 6) 유저 상태 업데이트
        // -----------------------------------------------------------------
        user.connectEmail(emailToConnect);
        user.verifyEmail();
        userRepository.save(user);

        // -----------------------------------------------------------------
        // 6-1)  EMAIL 인증 완료 시 ROLE_USER 보장 (소셜 EMAIL_REQUIRED 플로우 핵심 보완)
        // - 로컬 회원가입은 가입 시점에 ROLE_USER가 이미 존재
        // - 소셜 + EMAIL_REQUIRED 유저는 여기서 최초 ROLE_USER가 부여된다.
        // - USER_AUTHORITY가 비어 있는 상태를 절대 허용하지 않는다.
        // -----------------------------------------------------------------
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            Role userRole = roleRepository.findByRole(RoleName.ROLE_USER)
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));
            user.addRole(userRole);
            userRepository.save(user);
        }

        // -----------------------------------------------------------------
        // 7) 토큰 1회용 처리
        // -----------------------------------------------------------------
        token.markUsed();
        emailVerificationTokenRepository.save(token);

        // -----------------------------------------------------------------
        // 8) 1회용 code 발급 + Redis 저장 (TTL 5분)
        // -----------------------------------------------------------------
        String loginCode = UUID.randomUUID().toString();
        String redisKey = REDIS_KEY_PREFIX + loginCode;
        String redisValue = String.valueOf(user.getId());

        redisTemplate.opsForValue().set(redisKey, redisValue, 5, TimeUnit.MINUTES);

        log.info("[EMAIL VERIFY] success userId={}, code={}", user.getId(), loginCode);

        // -----------------------------------------------------------------
        // 9) 프론트 redirect URL 생성
        // -----------------------------------------------------------------
        String encodedCode = UriUtils.encode(loginCode, StandardCharsets.UTF_8);
        return frontendUrl + "/email-verified?code=" + encodedCode;
    }

    /**
     * ✅ 2) 프론트에서 code를 받아 최종 로그인 토큰 교환 (exchange)
     */
    @Transactional
    public JwtAuthenticationResponse exchangeCode(String code, LoginRequest loginRequest) {

        if (!StringUtils.hasText(code)) {
            throw new BadRequestException("Code is required");
        }

        if (loginRequest == null || loginRequest.getDeviceInfo() == null) {
            throw new BadRequestException("DeviceInfo is required");
        }
        if (!StringUtils.hasText(loginRequest.getDeviceInfo().getDeviceId())) {
            throw new BadRequestException("DeviceInfo.deviceId is required");
        }

        String redisKey = REDIS_KEY_PREFIX + code;

        Object valueObj = redisTemplate.opsForValue().get(redisKey);
        if (valueObj == null) {
            throw new ExpiredTokenException("Invalid or expired verification code");
        }

        Boolean deleted = redisTemplate.delete(redisKey);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new ResourceAlreadyInUseException("EmailVerificationCode", "code", code);
        }

        Long userId;
        try {
            userId = Long.valueOf(valueObj.toString());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid verification code payload", e);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadRequestException("Email not verified");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return authService.generateTokens(userDetails, loginRequest);
    }
}
