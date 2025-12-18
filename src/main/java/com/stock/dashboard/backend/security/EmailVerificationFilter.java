package com.stock.dashboard.backend.security;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailVerificationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    private static final List<String> REQUIRE_VERIFIED_PATHS = List.of(
            "/api/user",   // prefix 기준으로 바꿈 🔥
            "/api/post",
            "/api/order"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        boolean requiresVerified = REQUIRE_VERIFIED_PATHS.stream()
                .anyMatch(uri::startsWith);

        if (!requiresVerified) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = jwtTokenProvider.resolveToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = jwtTokenProvider.getUserIdFromJWT(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getEmailVerified()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("EMAIL_VERIFICATION_REQUIRED");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
