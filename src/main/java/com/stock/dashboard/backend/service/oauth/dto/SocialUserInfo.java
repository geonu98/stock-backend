package com.stock.dashboard.backend.service.oauth.dto;

public record SocialUserInfo (

    //레코드 문법
    // Getter/equals/hashCode/toString/생성자
    //전부 자동 생성되는 순수 데이터 전달 객체 전용 문법

    String provider,     // kakao / google / apple
    String providerId,   // 고유 ID
    String email,        // optional
    String nickname,     // optional
    String profileImage  // optional
){}
