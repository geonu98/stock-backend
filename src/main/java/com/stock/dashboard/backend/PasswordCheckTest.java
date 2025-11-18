package com.stock.dashboard.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordCheckTest {
    public static void main(String[] args) {

//비번 암호화 테스트 클래스 입니다
        String rawPassword = "password1234"; // 바꿀 비밀번호
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(rawPassword);

        System.out.println("암호화된 비밀번호: " + encodedPassword);
    }
}
