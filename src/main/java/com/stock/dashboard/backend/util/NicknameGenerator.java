package com.stock.dashboard.backend.util;

import java.security.SecureRandom;

public final class NicknameGenerator {
    private static final SecureRandom RND = new SecureRandom();

    private NicknameGenerator() {}

    public static String baseFrom(String displayNameOrEmailOrUsername) {
        String s = displayNameOrEmailOrUsername == null ? "" : displayNameOrEmailOrUsername.trim();
        if (s.isEmpty()) s = "user";

        // email이면 @ 앞까지만
        int at = s.indexOf('@');
        if (at > 0) s = s.substring(0, at);

        // 공백 제거
        s = s.replaceAll("\\s+", "");

        // 허용 문자만 남기기(한글/영문/숫자/_)
        s = s.replaceAll("[^0-9A-Za-z가-힣_]", "");

        if (s.isEmpty()) s = "user";
        if (s.length() > 12) s = s.substring(0, 12);

        return s;
    }

    public static String withSuffix(String base) {
        int n = 1000 + RND.nextInt(9000); // 4자리
        return base + n;
    }
}
