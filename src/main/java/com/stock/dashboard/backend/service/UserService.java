package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.exception.ResourceNotFoundException;
import com.stock.dashboard.backend.model.EmailVerificationToken;
import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.RoleName;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.payload.request.SignUpRequest;
import com.stock.dashboard.backend.model.payload.request.UpdatePasswordRequest;
import com.stock.dashboard.backend.model.payload.request.UpdateUserRequest;
import com.stock.dashboard.backend.model.payload.response.UserProfileResponse;
import com.stock.dashboard.backend.repository.EmailVerificationTokenRepository;
import com.stock.dashboard.backend.repository.RoleRepository;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.util.VerificationTokenCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.stock.dashboard.backend.util.NicknameGenerator;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    /**
     * ✅ 통합된 EmailService
     * - sendEmailVerification(toEmail, rawToken) 단일 메서드만 사용
     */
    private final EmailService emailService;

    /**
     * ✅ 이메일 인증 토큰(해시) 저장용 Repository
     * - rawToken은 저장하지 않고 hash만 저장한다.
     */
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    /**
     * ✅ rawToken 생성 + sha256 해시 계산 유틸(컴포넌트)
     * - 토큰 생성/해시 로직을 한 군데로 모아서 중복/실수 방지
     */
    private final VerificationTokenCodec verificationTokenCodec;

    /**
     * ✅ 이메일 인증 토큰 TTL(분)
     * - 통합 스펙에서 권장: 15분
     * - application.properties에 app.email.verify-token-ttl-minutes 값이 있으면 그걸 사용
     * - 없으면 기본값 15로 동작
     */
    @Value("${app.email.verify-token-ttl-minutes:15}")
    private long verifyTokenTtlMinutes;

    // ---------------------------------------------------------------------
    // ✅ 회원가입 (로컬) - JWT 이메일 인증 제거 + DB 토큰 방식으로 전환
    // ---------------------------------------------------------------------
    public User registerUser(SignUpRequest req) {

        // 1) 이메일 중복 체크
        // - 이미 가입된 이메일이면 회원가입 자체를 막는다.
        // - (추후 예외 표준화에서 409로 바꿀 예정)
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // 2) 비밀번호 인코딩
        String encodePw  = passwordEncoder.encode(req.getPassword());

        // 3) User 생성
        // - provider는 local
        // - emailVerified=false 로 시작 (메일 클릭 verify 성공 시 true가 됨)
        User user = new User(
                req.getEmail(),
                encodePw,
                req.getName(),
                req.getAge(),
                req.getPhoneNumber(),
                "local",
                false
        );
        //  닉네임 무조건 생성(유니크)
        ensureNickname(user);

        // 4) ROLE_USER 권한 부여
        Role userRole = roleRepository.findByRole(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role Not Found"));
        user.addRole(userRole);

        // 5) DB 저장 (userId가 있어야 토큰 테이블에 userId를 저장할 수 있음)
        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // nickname unique 충돌 대비: 한번 더 생성해서 재시도
            user.updateNickname(generateUniqueNickname(
                    (req.getName() != null && !req.getName().isBlank()) ? req.getName() : req.getEmail()
            ));
            savedUser = userRepository.save(user);
        }
        // -----------------------------------------------------------------
        // ✅ 여기부터가 "JWT 이메일 인증"을 완전히 대체하는 핵심 로직
        // -----------------------------------------------------------------

        // 6) rawToken 생성 (메일 링크에 들어갈 '원문 토큰')
        // - rawToken은 절대 DB에 저장하지 않는다.
        String rawToken = verificationTokenCodec.newRawToken();

        // 7) rawToken을 sha256으로 해시해서 DB에 저장할 값 생성
        // - DB에는 tokenHash만 저장 (rawToken 유출 방지)
        String tokenHash = verificationTokenCodec.sha256Hex(rawToken);

        // 8) EmailVerificationToken 엔티티 생성 + 저장
        // - TTL(기본 15분)을 적용해 expiresAt이 설정된다.
        EmailVerificationToken tokenEntity =
                EmailVerificationToken.create(
                        savedUser.getId(),
                        savedUser.getEmail(),
                        tokenHash,
                        Duration.ofMinutes(verifyTokenTtlMinutes)
                );

        emailVerificationTokenRepository.save(tokenEntity);

        // 9) 인증 메일 발송 (항상 백엔드 verify API 링크로 발송)
        // - 링크: {BACKEND}/api/auth/email/verify?token={rawToken}
        // - verify 성공 후 redirect는 추후 EmailVerificationService에서 담당
        emailService.sendEmailVerification(savedUser.getEmail(), rawToken);

        // 10) 저장된 user 반환
        return savedUser;
    }

    // ---------------------------------------------------------------------
    // 아래는 기존 기능 그대로 유지 (프로필/비밀번호 변경)
    // ---------------------------------------------------------------------

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return new UserProfileResponse(user);
    }

    public UserProfileResponse updateUserProfile(Long userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.updateProfile(req);
        userRepository.save(user);
        return new UserProfileResponse(user);
    }

    public void changePassword(Long userId, UpdatePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        String encodePw = passwordEncoder.encode(req.getNewPassword());
        user.updatePassword(encodePw);
        userRepository.save(user);
    }

    private String generateUniqueNickname(String displayNameOrEmailOrUsername) {
        String base = NicknameGenerator.baseFrom(displayNameOrEmailOrUsername);

        // 충돌 거의 없지만 안전하게 재시도
        for (int i = 0; i < 20; i++) {
            String candidate = NicknameGenerator.withSuffix(base);
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }

        // 극단적 케이스 fallback
        return base + (System.currentTimeMillis() % 100000);
    }

    /**
     * 저장 직전에 nickname이 비어있으면 채워주는 공통 보정
     */
    private void ensureNickname(User user) {
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            // 우선순위: name -> email -> username -> "user"
            String seed =
                    (user.getName() != null && !user.getName().isBlank()) ? user.getName()
                            : (user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail()
                            : (user.getUsername() != null && !user.getUsername().isBlank()) ? user.getUsername()
                            : "user";

            user.updateNickname(generateUniqueNickname(seed));
        }
    }
    public void ensureNicknameBeforeSave(User user, String preferredSeed) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return;
        }
        forceRegenerateNickname(user, preferredSeed);
    }

    public void ensureNicknameBeforeSave(User user) {
        ensureNicknameBeforeSave(user, null);
    }
    public void forceRegenerateNickname(User user, String preferredSeed) {
        String seed =
                (preferredSeed != null && !preferredSeed.isBlank()) ? preferredSeed
                        : (user.getName() != null && !user.getName().isBlank()) ? user.getName()
                        : (user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail()
                        : (user.getUsername() != null && !user.getUsername().isBlank()) ? user.getUsername()
                        : "user";

        user.updateNickname(generateUniqueNickname(seed));
    }
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
