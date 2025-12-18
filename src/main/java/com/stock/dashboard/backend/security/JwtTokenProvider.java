package com.stock.dashboard.backend.security;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long jwtExpirationInMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration}") long jwtExpirationInMs) {

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    /**
     * 사용자 객체로부터 JWT 토큰 생성
     */
    public String generateToken(CustomUserDetails userDetails) {
        Instant expiryDate = Instant.now().plusMillis(jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(Long.toString(userDetails.getId()))
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(expiryDate))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }


    /*
    이메일 인증용
     */
    public String generateEmailVerificationToken(User user) {
        Long userId = user.getId();
        String email = user.getEmail();
     return  createEmailVerificationToken(userId, email);
    }

        // 실제 JWT 생성은 여기서 처리 → 내부 전용 메서드로 분리
        private String createEmailVerificationToken(Long userId, String email) {

            long emailVerificationExpirationMs = 15 * 60 * 1000; // 15분
            Instant expiryDate = Instant.now().plusMillis(emailVerificationExpirationMs);

            return Jwts.builder()
                    .setSubject(String.valueOf(userId))
                    .claim("email", email)
                    .claim("purpose", "email_verification")
                    .setIssuedAt(Date.from(Instant.now()))
                    .setExpiration(Date.from(expiryDate))
                    .signWith(key, SignatureAlgorithm.HS512)
                    .compact();
        }

//    /**
//     * userId로부터 JWT 토큰 생성
//     */
//    public String generateTokenFromUserId(Long userId) {
//        Instant expiryDate = Instant.now().plusMillis(jwtExpirationInMs);
//
//        return Jwts.builder()
//                .setSubject(Long.toString(userId))
//                .setIssuedAt(Date.from(Instant.now()))
//                .setExpiration(Date.from(expiryDate))
//                .signWith(key, SignatureAlgorithm.HS512)
//                .compact();
//    }

    /**
     * JWT 토큰에서 userId 추출
     */
    public Long getUserIdFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * JWT 토큰 만료일자 추출
     */
    public Date getTokenExpiryFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getExpiration();
    }

    /**
     * JWT 만료 시간(ms) 반환
     */
    public long getExpiryDuration() {
        return jwtExpirationInMs;
    }

    public Long getJwtExpirationInMs() {
        return jwtExpirationInMs;
    }

    public Claims getAllClaimsFromToken(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * HTTP 요청 헤더에서 JWT 토큰 추출
     *
     * - 클라이언트는 Authorization 헤더에
     *   "Bearer {JWT}" 형식으로 토큰을 전달함
     * - "Bearer " 접두사를 제거하고 실제 토큰만 반환
     * - Authorization 헤더가 없거나 형식이 올바르지 않으면 null 반환
     */
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            // "Bearer " 이후의 토큰 문자열만 반환
            return bearerToken.substring(7);
        }

        return null;
    }

    /**
     * 전달받은 JWT 토큰의 유효성 검증
     *
     * - 서명(Signature) 검사
     * - 토큰의 만료 시간 확인
     * - 형식(Json Structure) 검사
     *
     * 검증 중 하나라도 실패하면 예외 발생 → false 반환
     * 정상일 경우 true 반환
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key) // 토큰 서명 키 설정
                    .build()
                    .parseClaimsJws(token); // 토큰 파싱 + 유효성 검증
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }


}
