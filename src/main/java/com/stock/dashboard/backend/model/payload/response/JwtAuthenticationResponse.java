package com.stock.dashboard.backend.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtAuthenticationResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private Long expiryDuration;

    // 신규 회원 여부를 프론트로 전달하기 위한 필드
    @JsonProperty("isNewUser")
    private boolean isNewUser;

    // 프론트에서 추가정보 입력 시 필요한 userId
    @JsonProperty("id")
    private Long userId;

    /**
     * 기본 생성자 (Lombok이 자동 생성)
     */
    public JwtAuthenticationResponse() {
        this.tokenType = "Bearer ";
    }

    /**
     * AccessToken만 있는 경우 (테스트용)
     */
    public JwtAuthenticationResponse(String accessToken) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer ";
    }

    /**
     * AccessToken + RefreshToken + 만료시간
     */
    public JwtAuthenticationResponse(String accessToken, String refreshToken, Long expiryDuration) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiryDuration = expiryDuration;
        this.tokenType = "Bearer ";
    }

    /**
     * AccessToken + RefreshToken + 만료시간 + 신규회원여부 + userId
     */
    public JwtAuthenticationResponse(String accessToken, String refreshToken, Long expiryDuration, boolean isNewUser, Long userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiryDuration = expiryDuration;
        this.tokenType = "Bearer ";
        this.isNewUser = isNewUser;
        this.userId = userId;
    }
}
