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

    /**
     * ✅ JWT 필터를 적용하지 않을 경로 prefix 목록
     * - 로그인/회원가입/토큰재발급/소셜로그인 시작/콜백은 "로그인 전" 요청이므로 JWT 검사하면 안 됨
     */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/auth",          // ✅ /api/auth/login, /api/auth/refresh, /api/auth/oauth/... 전부 포함
            "/swagger",
            "/swagger-ui",
            "/swagger-resources",
            "/v3/api-docs",
            "/api-docs",
            "/error"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // ✅ URI 기준이 제일 안전함 (servletPath보다 덜 헷갈림)
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS preflight는 무조건 패스
        if ("OPTIONS".equalsIgnoreCase(method)) return true;

        boolean skip = EXCLUDED_PREFIXES.stream().anyMatch(uri::startsWith);
        log.info("[JwtFilter] shouldNotFilter? {} {} -> {}", method, uri, skip);
        return skip;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);
        log.debug("[JwtFilter] Incoming JWT: {}", jwt);

        try {
            // ✅ 토큰이 없으면 그냥 통과 (permitAll 경로/익명 요청 가능)
            if (!StringUtils.hasText(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // ✅ 토큰이 있으면 검증 후 인증 세팅
            if (jwtTokenValidator.validateToken(jwt)) {
                Long userId = jwtTokenProvider.getUserIdFromJWT(jwt);
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

        } catch (Exception ex) {
            log.error("[JwtFilter] Failed to authenticate user:", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(tokenRequestHeader);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(tokenRequestHeaderPrefix)) {
            return bearerToken.replace(tokenRequestHeaderPrefix, "").trim();
        }
        return null;
    }
}
