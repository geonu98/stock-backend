package com.stock.dashboard.backend.config;

import com.stock.dashboard.backend.security.EmailVerificationFilter;
import com.stock.dashboard.backend.security.JwtAuthenticationEntryPoint;
import com.stock.dashboard.backend.security.JwtAuthenticationFilter;
import com.stock.dashboard.backend.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity(debug = true)
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 이메일 인증 검사 필터 (JWT 이후 실행)
    private final EmailVerificationFilter emailVerificationFilter;

    /**
     * ✅ Render 환경변수에서 주입받는 허용 Origin 목록
     * 예: https://stock-frontend-five-delta.vercel.app
     * 여러 개면 콤마로: https://a.vercel.app,https://b.vercel.app
     *
     * - application-prod.properties에 아래처럼 연결해두는 걸 권장
     *   app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
     */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    // BCrypt PasswordEncoder Bean
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager Bean
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // DaoAuthenticationProvider Bean 연결
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * ✅ CORS 설정 Bean
     * - Vercel(프론트) → Render(백엔드) 호출은 "다른 Origin"이라 브라우저가 막는다.
     * - 백엔드에서 허용 Origin을 명시적으로 열어줘야 함.
     *
     * 주의:
     * - allowCredentials(true)일 때 allowedOrigins에 "*" 못 쓴다.
     * - 그래서 "허용 도메인 목록"을 env로 관리하는 방식이 정답.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // env가 비어있으면(로컬/설정누락) 일단 개발 편의로 localhost 허용(원하면 제거 가능)
        // 실제 운영은 Render env(CORS_ALLOWED_ORIGINS)로만 관리하는 걸 추천
        List<String> origins;
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            origins = List.of("http://localhost:5173", "http://localhost:3000");
        } else {
            origins = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        config.setAllowedOrigins(origins);

        // 기본 REST + preflight
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 요청 헤더는 일단 전체 허용(Authorization 포함)
        config.setAllowedHeaders(List.of("*"));

        // 클라이언트가 Authorization 헤더를 응답에서 읽을 수 있게(필요시)
        config.setExposedHeaders(List.of("Authorization"));

        // 쿠키/인증정보 포함 요청 허용 (추후 쿠키 기반 RT 쓰게 되면 필수)
        config.setAllowCredentials(true);

        // 모든 경로에 CORS 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ✅ CORS 활성화 (corsConfigurationSource Bean을 자동으로 사용)
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger + 정적 리소스 허용
                        .requestMatchers(
                                "/swagger-resources/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/swagger",
                                "/api-docs",
                                "/",
                                "/favicon.ico",
                                "/error",
                                "/api/market/**",
                                "/api/home/**"
                        ).permitAll()

                        // 인증 없이 접근 가능한 API
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/auth/oauth/**").permitAll()
                        .requestMatchers("/", "/health").permitAll()

                        // 인증 이후 접근 가능한 API
                        .requestMatchers("/api/secure/**").authenticated()
                        .requestMatchers("/api/user/me").authenticated()

                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider());

        // ✅ JWT 인증 필터를 UsernamePasswordAuthenticationFilter 전에 실행
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // ✅ 이메일 미인증 사용자의 기능 접근 제한 필터
        // 반드시 JWT 인증 이후 실행해야 하므로 addFilterAfter 사용
        http.addFilterAfter(emailVerificationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
