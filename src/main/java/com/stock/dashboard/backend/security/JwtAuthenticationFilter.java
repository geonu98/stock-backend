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

    //  JWT 필터를 적용하지 않을 경로 (소셜 로그인 확장 포함)
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/",      // 회원가입, 로그인, 이메일 인증 등
            "/api/auth/oauth",     //  명시적으로 추가 (가독성용)
            "/api/auth/oauth",     //  명시적으로 추가
            "/oauth2/",        // OAuth2 인증 시작
            "/login/oauth2/",  // 소셜 로그인 Redirect URI
            "/swagger",
            "/v3/api-docs",
            "/api-docs",
            "/error"
    );

    //  shouldNotFilter → true면 필터 자체를 실행하지 않음
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        log.warn("[DEBUG] shouldNotFilter() method={} path={}", method, path);

        // OPTIONS는 무조건 예외
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.warn("[DEBUG] → skip OPTIONS");
            return true;
        }

        boolean skip = EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
        log.warn("[DEBUG] EXCLUDED? {} → {}", path, skip);

        return skip;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);
        log.info("[JwtFilter] Incoming JWT: {}", jwt);

        try {
            if (StringUtils.hasText(jwt) && jwtTokenValidator.validateToken(jwt)) {
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
