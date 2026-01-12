package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.exception.BadRequestException;
import com.stock.dashboard.backend.exception.ExpiredTokenException;
import com.stock.dashboard.backend.exception.ResourceAlreadyInUseException;
import com.stock.dashboard.backend.model.EmailVerificationToken;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.repository.EmailVerificationTokenRepository;
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
     *
     * 이메일 링크 예)
     *   GET /api/auth/email/verify?token=xxxx(rawToken)
     *
     * 흐름
     * 0) token 파라미터 검증
     * 1) rawToken -> tokenHash 변환(sha256)
     * 2) DB에서 tokenHash로 EmailVerificationToken 조회
     * 3) 만료/사용 여부 체크
     * 4) userId로 사용자 조회
     * 5) (핵심) 해당 이메일이 "다른 사용자"에게 이미 사용중인지 체크
     * 6) user.email = token.email, user.emailVerified=true 처리
     * 7) token.usedAt 기록(1회용 처리)
     * 8) Redis에 1회용 code 저장 (TTL 5분)
     * 9) 프론트 redirect URL 반환 (/email-verified?code=...)
     *
     * ✅ 상태코드
     * - token 누락/위조: 400
     * - token 만료: 410
     * - token 재사용: 409
     * - 이메일 충돌: 409
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
        // - DB에는 rawToken을 저장하지 않고, tokenHash만 저장한다.
        // - 따라서 조회도 hash로만 한다.
        // -----------------------------------------------------------------
        String tokenHash = verificationTokenCodec.sha256Hex(rawToken);

        // -----------------------------------------------------------------
        // 2) DB 조회: 없으면 위조/잘못된 token -> 400
        // -----------------------------------------------------------------
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid email verification token"));

        // -----------------------------------------------------------------
        // 3) 만료/사용 여부 체크
        // - 만료: 410
        // - 이미 사용됨: 409
        // -----------------------------------------------------------------
        if (token.isExpired()) {
            throw new ExpiredTokenException("Email verification token expired");
        }
        if (token.isUsed()) {
            throw new ResourceAlreadyInUseException("EmailVerificationToken", "token", "already used");
        }

        // -----------------------------------------------------------------
        // 4) 토큰에 들어있는 userId로 사용자 조회
        // - 토큰은 userId만 들고 있으므로, 연관관계(token.getUser())는 없다.
        // -----------------------------------------------------------------
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String emailToConnect = token.getEmail();

        // -----------------------------------------------------------------
        // 5) (핵심) 이메일 중복 방지 -> 409
        // - 이 이메일이 이미 다른 사용자에게 사용 중이면 연결하면 안 된다.
        // - 단, "내가 이미 이 이메일을 가진 상태"면 통과 가능.
        // -----------------------------------------------------------------
        userRepository.findByEmail(emailToConnect)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ResourceAlreadyInUseException("User", "email", emailToConnect);
                });

        // -----------------------------------------------------------------
        // 6) 유저 상태 업데이트
        // - 소셜 EMAIL_REQUIRED: connectEmail(email)로 email을 확정
        // - 로컬 signup: 이미 email이 있을 수 있으나, 안전하게 동일 값으로 세팅해도 무방
        // - 그리고 emailVerified=true 로 인증 완료 처리
        // -----------------------------------------------------------------
        user.connectEmail(emailToConnect); // 네 User 엔티티에 존재하는 메서드 전제
        user.verifyEmail();                // emailVerified=true 처리 전제
        userRepository.save(user);

        // -----------------------------------------------------------------
        // 7) 토큰 1회용 처리
        // - usedAt 기록하여 링크 2번 클릭 시 409로 막는다.
        // -----------------------------------------------------------------
        token.markUsed();
        emailVerificationTokenRepository.save(token);

        // -----------------------------------------------------------------
        // 8) 1회용 code 발급 + Redis 저장 (TTL 5분)
        // - URL에는 AT/RT 절대 실어 보내지 않음
        // - URL에는 짧은 TTL의 code만 전달
        // -----------------------------------------------------------------
        String loginCode = UUID.randomUUID().toString();
        String redisKey = REDIS_KEY_PREFIX + loginCode;
        String redisValue = String.valueOf(user.getId());

        redisTemplate.opsForValue().set(redisKey, redisValue, 5, TimeUnit.MINUTES);

        log.info("[EMAIL VERIFY] success userId={}, code={}", user.getId(), loginCode);

        // -----------------------------------------------------------------
        // 9) 프론트 redirect URL 생성
        // - code는 URL 파라미터로 들어가므로 인코딩을 습관적으로 적용
        // -----------------------------------------------------------------
        String encodedCode = UriUtils.encode(loginCode, StandardCharsets.UTF_8);
        return frontendUrl + "/email-verified?code=" + encodedCode;
    }

    /**
     * ✅ 2) 프론트에서 code를 받아 최종 로그인 토큰 교환 (exchange)
     *
     * POST /api/auth/email/exchange
     * body: { code, deviceInfo }
     *
     * 흐름
     * 0) 입력 검증(code/deviceInfo)
     * 1) Redis에서 code 조회 (없으면 만료 -> 410)
     * 2) Redis delete 결과 체크 (1회용 소모 강화) -> 실패면 409
     * 3) userId로 유저 조회
     * 4) emailVerified=true 재확인 (아니면 400)
     * 5) authService.generateTokens로 AT/RT 발급
     */
    @Transactional
    public JwtAuthenticationResponse exchangeCode(String code, LoginRequest loginRequest) {

        // -----------------------------------------------------------------
        // 0) 입력 검증
        // -----------------------------------------------------------------
        if (!StringUtils.hasText(code)) {
            throw new BadRequestException("Code is required");
        }

        // verify 단계에서는 deviceInfo를 받지 않는다.
        // exchange 단계에서만 "실제 로그인할 디바이스"가 확정되므로 여기서만 받는다.
        if (loginRequest == null || loginRequest.getDeviceInfo() == null) {
            throw new BadRequestException("DeviceInfo is required");
        }
        if (!StringUtils.hasText(loginRequest.getDeviceInfo().getDeviceId())) {
            throw new BadRequestException("DeviceInfo.deviceId is required");
        }

        String redisKey = REDIS_KEY_PREFIX + code;

        // -----------------------------------------------------------------
        // 1) Redis 조회
        // - 없으면 만료/위조/잘못된 code -> 410
        // -----------------------------------------------------------------
        Object valueObj = redisTemplate.opsForValue().get(redisKey);
        if (valueObj == null) {
            throw new ExpiredTokenException("Invalid or expired verification code");
        }

        // -----------------------------------------------------------------
        // 2) 1회용 소모 강화
        // - delete가 true가 아니면 이미 누가 먼저 소모했거나 중복 요청 -> 409
        // -----------------------------------------------------------------
        Boolean deleted = redisTemplate.delete(redisKey);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new ResourceAlreadyInUseException("EmailVerificationCode", "code", code);
        }

        // -----------------------------------------------------------------
        // 3) userId 파싱
        // -----------------------------------------------------------------
        Long userId;
        try {
            userId = Long.valueOf(valueObj.toString());
        } catch (NumberFormatException e) {
            // Redis 값이 예상과 다르면 서버 구성 문제지만,
            // 프론트에는 요청 오류로 반환하는 편이 안전
            throw new BadRequestException("Invalid verification code payload", e);
        }

        // -----------------------------------------------------------------
        // 4) 유저 조회 + 이메일 인증 상태 재확인
        // -----------------------------------------------------------------
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadRequestException("Email not verified");
        }

        // -----------------------------------------------------------------
        // 5) 최종 토큰 발급 (AT/RT)
        // - 이 시점이 "로그인 완료"다.
        // -----------------------------------------------------------------
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return authService.generateTokens(userDetails, loginRequest);
    }
}
