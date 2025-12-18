package com.stock.dashboard.backend.config;

import com.stock.dashboard.backend.security.JwtAuthenticationEntryPoint;
import com.stock.dashboard.backend.security.JwtAuthenticationFilter;
import com.stock.dashboard.backend.security.EmailVerificationFilter;
import com.stock.dashboard.backend.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity(debug = true)
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    //   이메일 인증 검사 필터
    private final EmailVerificationFilter emailVerificationFilter;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
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
                                "/favicon.ico"
                        ).permitAll()
                        // 인증 없이 접근 가능한 API
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/auth/oauth/**").permitAll()

                        // 인증 이후 접근 가능한 API
                        .requestMatchers("/api/secure/**").authenticated()
                        .requestMatchers("/api/user/me").authenticated()

                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider());

        //  JWT 인증 필터를 UsernamePasswordAuthenticationFilter 전에 실행
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        //  이메일 미인증 사용자의 기능 접근 제한 필터 추가
        // → 반드시 JWT 인증 이후 실행해야 하므로 addFilterAfter 사용
        http.addFilterAfter(emailVerificationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
