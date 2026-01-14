package com.stock.dashboard.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 기존 프로바이더 + 프로바이더아이디 넘 길어서
 * 짧게 만들수 있는 유틸
 */
public final class UsernameGenerator {

    private static final int MAX_LENGTH = 30;
    private static final int HASH_LENGTH = 20;

    private UsernameGenerator() {}

    public static String generate(String provider, String providerId) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId is required");
        }

        String prefix = provider.toLowerCase();
        String hash = shortHash(prefix + ":" + providerId);

        String username = prefix + "_" + hash;

        return username.length() > MAX_LENGTH
                ? username.substring(0, MAX_LENGTH)
                : username;
    }

    private static String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
