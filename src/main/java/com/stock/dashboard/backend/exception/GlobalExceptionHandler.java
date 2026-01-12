package com.stock.dashboard.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        "BAD_REQUEST",
                        e.getMessage()
                ));
    }

    @ExceptionHandler(ResourceAlreadyInUseException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ResourceAlreadyInUseException e) {
        return ResponseEntity
                .status(409)
                .body(new ErrorResponse(
                        "RESOURCE_ALREADY_IN_USE",
                        e.getMessage()
                ));
    }

    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ErrorResponse> handleExpired(ExpiredTokenException e) {
        return ResponseEntity
                .status(410)
                .body(new ErrorResponse(
                        "TOKEN_EXPIRED",
                        e.getMessage()
                ));
    }

    /**
     * AuthService 등에서 던진 BadCredentialsException 커스터마이즈
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException e
    ) {
        String code = "INVALID_CREDENTIALS";
        String message = "이메일 또는 비밀번호가 올바르지 않습니다.";

        if ("EMAIL_NOT_VERIFIED".equals(e.getMessage())) {
            code = "EMAIL_NOT_VERIFIED";
            message = "이메일 인증이 필요합니다.";
        }

        return ResponseEntity
                .status(401)
                .body(new ErrorResponse(code, message));
    }
}
