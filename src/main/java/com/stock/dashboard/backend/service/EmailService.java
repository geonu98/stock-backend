package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final  JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")  // Gmail 계정
    private String senderEmail;

    public void sendVerificationEmail(User user, String token) {

        String verifyUrl = frontendUrl + "/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        //  From 설정 (표시되는 발신자명 변경)
        message.setFrom(String.format("Stock Dashboard <%s>", senderEmail));

        message.setTo(user.getEmail());
        message.setSubject("[Stock Dashboard] 이메일 인증을 완료해주세요");
        message.setText("""
                아래 링크를 클릭하여 이메일 인증을 완료해주세요:

                %s

                15분 후 만료됩니다.
                """.formatted(verifyUrl));

        mailSender.send(message);
        log.info("인증메일 발송 완료: {}", user.getEmail());


    }
}
