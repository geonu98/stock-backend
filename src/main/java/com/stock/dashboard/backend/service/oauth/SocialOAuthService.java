package com.stock.dashboard.backend.service.oauth;

import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;

public interface SocialOAuthService {

    // provider 문자열이 해당 서비스에서 처리 가능한지 판단
    boolean supports(String provider);

    // OAuth code로 User 정보 획득
    SocialUserInfo getUserInfo(String code);
//로그인 URL 생성
    String getAuthorizeUrl(String redirectUri);
 }
