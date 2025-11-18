package com.stock.dashboard.backend.security;

import com.stock.dashboard.backend.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenValidator jwtTokenValidator;
    private final CustomUserDetailsService customUserDetailsService;

    @Value("${app.jwt.header}")
    private String tokenRequestHeader;

    @Value("${app.jwt.header.prefix}")
    private String tokenRequestHeaderPrefix;

    // 공개 URL 설정
    private static final List<String> PUBLIC_URLS = List.of(
            "/api/auth", "/swagger", "/v3/api-docs", "/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        log.info("[JwtFilter] Incoming request path: {}", path);

        // 공개 URL이면 필터 무시
        for (String publicPath : PUBLIC_URLS) {
            if (path.startsWith(publicPath)) {
                log.info("[JwtFilter] Public URL, skipping authentication: {}", path);
                filterChain.doFilter(request, response);
                return;
            }
        }

        try {
            String jwt = getJwtFromRequest(request);
            log.info("[JwtFilter] Extracted JWT: {}", jwt);

            if (StringUtils.hasText(jwt) && jwtTokenValidator.validateToken(jwt)) {
                log.info("[JwtFilter] Token is valid, extracting user ID...");
                Long userId = jwtTokenProvider.getUserIdFromJWT(jwt);
                log.info("[JwtFilter] User ID from token: {}", userId);

                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                log.info("[JwtFilter] Loaded user details: {}", userDetails.getUsername());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("[JwtFilter] Authentication set in SecurityContext");
            } else {
                log.warn("[JwtFilter] No valid JWT found in request headers");
            }

        } catch (Exception ex) {
            log.error("[JwtFilter] Failed to set user authentication in security context: ", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(tokenRequestHeader);
        log.info("[JwtFilter] Authorization header: {}", bearerToken);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(tokenRequestHeaderPrefix)) {
            String token = bearerToken.replace(tokenRequestHeaderPrefix, "").trim();
            log.info("[JwtFilter] JWT after prefix removal: {}", token);
            return token;
        }
        return null;
    }
}
