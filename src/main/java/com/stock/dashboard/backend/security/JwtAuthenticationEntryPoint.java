package com.stock.dashboard.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

// 🛑 javax -> jakarta 로 변경!
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    // @Qualifier는 일단 유지하여 기존 로직대로 동작하게 합니다.
    @Autowired
    public JwtAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse httpServletResponse, AuthenticationException ex) throws IOException {
        log.error("User is unauthorised. Routing from the entry point");

        // 🛑 "javax.servlet.error.exception" -> "jakarta.servlet.error.exception" 으로 변경!
        if (request.getAttribute("jakarta.servlet.error.exception") != null) {
            Throwable throwable = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
            resolver.resolveException(request, httpServletResponse, null, (Exception) throwable);
        }

        httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
    }
}