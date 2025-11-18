package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.cache.LoggedOutJwtTokenCache;
import com.stock.dashboard.backend.event.OnUserLogoutSuccessEvent;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.UserDevice;
import com.stock.dashboard.backend.model.payload.request.LogOutRequest;
import com.stock.dashboard.backend.model.payload.request.LoginRequest;
import com.stock.dashboard.backend.model.payload.response.JwtAuthenticationResponse;
import com.stock.dashboard.backend.model.token.RefreshToken;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import com.stock.dashboard.backend.security.JwtTokenValidator;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenValidator jwtTokenValidator;
    private final LoggedOutJwtTokenCache logoutTokenCache;
    private final RefreshTokenService refreshTokenService;
    private final UserDeviceService userDeviceService; //

    /**
     * 사용자 인증 메소드
     * @param loginRequest 클라이언트에서 들어온 이메일/비밀번호 정보
     * @return Optional<Authentication> 인증 성공 시 Authentication 객체 반환
     */
    public Optional<Authentication> authenticateUser(LoginRequest loginRequest) {
        log.info("[AuthService] Authenticating user: {}", loginRequest.getEmail());

        // 1️⃣ DB에서 사용자 존재 확인
        Optional<User> optionalUser = userRepository.findByEmail(loginRequest.getEmail());
        if (optionalUser.isEmpty()) {
            log.warn("[AuthService] User NOT found in DB: {}", loginRequest.getEmail());
            return Optional.empty();
        } else {
            log.info("[AuthService] User found in DB: {}", loginRequest.getEmail());
        }

        User user = optionalUser.get();

        // 2️⃣ DB 비밀번호와 입력 비밀번호 비교 로그 (디버깅용)
        log.info("[AuthService DEBUG] DB stored password: {}", user.getPassword());
        log.info("[AuthService DEBUG] Raw password from request: {}", loginRequest.getPassword());
        boolean matches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        log.info("[AuthService DEBUG] Does raw password match DB password? {}", matches);

        // 3️⃣ 비밀번호 확인
        if (!matches) {
            log.warn("[AuthService] Password mismatch for user: {}", loginRequest.getEmail());
            // ❌ 로그인 실패 시 BadCredentialsException 발생
            throw new BadCredentialsException("Invalid password");
        } else {
            log.info("[AuthService] Password matched for user: {}", loginRequest.getEmail());
        }

        // 4️⃣ Authentication 객체 생성 (Spring Security용)
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        log.info("[AuthService] Authentication object created for user: {}", loginRequest.getEmail());

        // 5️⃣ Optional로 Authentication 객체 반환
        return Optional.of(authentication);
    }

    /**
     * JWT + Refresh Token 생성
     * @param userDetails CustomUserDetails 객체
     * @param loginRequest 로그인 요청(디바이스 정보 포함)
     * @return AccessToken + RefreshToken 응답 DTO
     */
    public JwtAuthenticationResponse generateTokens(CustomUserDetails userDetails, LoginRequest loginRequest) { // ✅ 시그니처 변경
        log.info("[AuthService] Generating tokens for user: {}", userDetails.getUsername());

        // Access Token 생성
        String accessToken = jwtTokenProvider.generateToken(userDetails);

        // Refresh Token 생성
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ✅ 변경된 코드: UserDeviceService 로 디바이스 등록/갱신 처리
        UserDevice userDevice = userDeviceService.createOrUpdateDeviceInfo(
                user,
                loginRequest.getDeviceInfo().getDeviceId(),
                loginRequest.getDeviceInfo().getDeviceType()
        );

        // ✅ 기존 토큰이 있는지 먼저 조회
        Optional<RefreshToken> existingTokenOpt = refreshTokenService.findByUserDevice(userDevice);

        RefreshToken refreshToken;
        if (existingTokenOpt.isPresent()) {
            // 기존 RefreshToken 갱신
            refreshToken = existingTokenOpt.get();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiryDt(refreshTokenService.generateExpiryDate());
            refreshToken.setUpdatedAt(LocalDateTime.now());
            log.info("[AuthService] 기존 RefreshToken 갱신 - userDeviceId={}, token={}",
                    userDevice.getId(), refreshToken.getToken());
        } else {
            // 새 RefreshToken 생성
            refreshToken = refreshTokenService.createRefreshToken(userDevice);
            log.info("[AuthService] 새 RefreshToken 생성 - userDeviceId={}, token={}",
                    userDevice.getId(), refreshToken.getToken());
        }

        refreshTokenService.save(refreshToken); // ✅ save() 명확히 호출 (upsert용)

        // ✅ Access + Refresh 토큰을 DTO 형태로 반환
        return new JwtAuthenticationResponse(
                accessToken,
                refreshToken.getToken(),
                jwtTokenProvider.getJwtExpirationInMs(), // access token 만료시간
                false,                                   // 신규회원 여부
                user.getId()                             // 사용자 ID
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
            throw new RuntimeException("No JWT token found in request header"); // 나중에 예외처리 리펙토링 예정
        }

        String token = header.substring(7);
        // 토큰 유효성 검사
        jwtTokenValidator.validateToken(token);

        // 사용자 ID 추출
        Long userId = jwtTokenProvider.getUserIdFromJWT(token);
        log.info("[Logout] Logging out user with ID: {}", userId);

        // LogOutRequest 객체 생성 (필요 시 필드 확장 가능)
        LogOutRequest logOutRequest = new LogOutRequest();
        logOutRequest.setDeviceInfo(null); // 디바이스 정보 관리한다면 여기에 설정 가능

        // 로그아웃 이벤트 객체 생성
        OnUserLogoutSuccessEvent logoutEvent = new OnUserLogoutSuccessEvent(
                userId.toString(), // 사용자 PK값
                token,
                logOutRequest
                // 날짜는 생성자에서 자동 추가됨
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

        // 만료 확인
        refreshTokenService.verifyExpiration(refreshToken);

        UserDevice userDevice = refreshToken.getUserDevice();
        User user = userDevice.getUser();

        // ✅ 새로운 Access Token 생성
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        log.info("[AuthService] Access Token 재발급 성공 - user: {}", user.getEmail());

        // 응답 생성 (기존 Refresh Token 그대로 반환)
        return new JwtAuthenticationResponse(
                newAccessToken,
                refreshToken.getToken(),
                jwtTokenProvider.getJwtExpirationInMs(),
                false,
                user.getId()
        );
    }
}
