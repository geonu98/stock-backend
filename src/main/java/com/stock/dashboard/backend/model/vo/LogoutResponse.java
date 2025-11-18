package com.stock.dashboard.backend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {
    private boolean success;
    private String message;
    private String kakaoLogoutUrl;
}
