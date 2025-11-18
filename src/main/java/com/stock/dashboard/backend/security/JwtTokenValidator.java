package com.stock.dashboard.backend.security;

import com.stock.dashboard.backend.cache.LoggedOutJwtTokenCache;
import com.stock.dashboard.backend.event.OnUserLogoutSuccessEvent;
import com.stock.dashboard.backend.exception.InvalidTokenRequestException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenValidator {

    private final SecretKey key;
    private final LoggedOutJwtTokenCache loggedOutTokenCache;

    public JwtTokenValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            LoggedOutJwtTokenCache loggedOutTokenCache) {

        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.loggedOutTokenCache = loggedOutTokenCache;
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .setSigningKey(key)
                    .parseClaimsJws(authToken); // 0.11.5 기준 검증

        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
            throw new InvalidTokenRequestException("JWT", authToken, "Incorrect signature");

        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
            throw new InvalidTokenRequestException("JWT", authToken, "Malformed jwt token");

        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
            throw new InvalidTokenRequestException("JWT", authToken, "Token expired. Refresh required");

        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
            throw new InvalidTokenRequestException("JWT", authToken, "Unsupported JWT token");

        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty.");
            throw new InvalidTokenRequestException("JWT", authToken, "Illegal argument token");
        }

        validateTokenIsNotForALoggedOutDevice(authToken);
        return true;
    }

    private void validateTokenIsNotForALoggedOutDevice(String authToken) {
        OnUserLogoutSuccessEvent previouslyLoggedOutEvent = loggedOutTokenCache.getLogoutEventForToken(authToken);
        if (previouslyLoggedOutEvent != null) {
            String userEmail = previouslyLoggedOutEvent.getUserIdentifier();
            Date logoutEventDate = previouslyLoggedOutEvent.getEventTime();
            String errorMessage = String.format(
                    "Token corresponds to an already logged out user [%s] at [%s]. Please login again",
                    userEmail, logoutEventDate);
            throw new InvalidTokenRequestException("JWT", authToken, errorMessage);
        }
    }
}
