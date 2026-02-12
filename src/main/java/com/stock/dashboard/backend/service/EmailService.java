package com.stock.dashboard.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    /**
     * JavaMailSender
     * - 실제 메일 전송 담당
     * - spring.mail.* 설정을 기반으로 동작
     *
     * ✅ (변경) Render 환경에서 Gmail SMTP(587/465) 연결이 막히는 케이스가 발생해서
     *    현재는 JavaMailSender를 사용하지 않고, Resend API(HTTP)로 전송한다.
     *    - 메일 "내용/링크 생성 로직"은 그대로 유지
     *    - "전송 수단"만 Resend로 교체
     *
     * ⚠️ 참고
     * - 기존 JavaMailSender 기반 구현은 SMTP 환경에서만 동작
     * - Resend는 outbound 포트 제약과 무관하게 안정적으로 동작
     */
    // private final JavaMailSender mailSender;

    /**
     * ✅ Resend 이메일 전송 클라이언트
     * - https://api.resend.com/emails 로 HTTP POST
     * - Render/Vercel 등 PaaS 환경에서 SMTP 포트 차단 이슈 없이 동작
     *
     * 필요 환경변수:
     * - RESEND_API_KEY
     * - RESEND_FROM  (예: "Stock Dashboard <onboarding@resend.dev>" 또는 도메인 인증 후 커스텀 주소)
     *
     * application*.properties:
     * - resend.api-key=${RESEND_API_KEY}
     * - resend.from=${RESEND_FROM}
     */
    private final ResendEmailClient resendEmailClient;

    /**
     * ✅ 백엔드 Base URL
     *
     * "이메일 인증 링크"는 프론트가 아니라 반드시 백엔드의 verify API를 가리켜야 한다.
     *
     * 예)
     *   http://localhost:8080
     *
     * 최종 링크는 아래 형태로 만들어진다:
     *   {backendBaseUrl}/api/auth/email/verify?token={rawToken}
     */
    @Value("${app.backend.url}")
    private String backendBaseUrl;

    /**
     * 발신자 이메일
     * - 보통 Gmail 계정 / SMTP 계정
     * - spring.mail.username 값을 그대로 사용
     *
     * ✅ (변경) Resend로 전환하면서 spring.mail.username 기반 발신자 주소는 사용하지 않는다.
     *    - Resend의 발신자(from)는 resend.from(${RESEND_FROM})에서 가져온다.
     *    - 기존 주석/설명은 유지 (이전 SMTP 구조에 대한 문서 역할)
     */
    // @Value("${spring.mail.username}")
    // private String senderEmail;

    // ---------------------------------------------------------------------
    // ✅ 통합된 단일 메서드 (로컬 회원가입 / 소셜 EMAIL_REQUIRED 모두 여기만 사용)
    // ---------------------------------------------------------------------

    /**
     * 이메일 인증 메일 발송 (단일 메서드)
     *
     * ✅ 이 메서드는 "메일 유형"과 무관하게 항상 동일하게 사용한다.
     *
     * - 로컬 회원가입(signup): user.email로 발송
     * - 소셜 EMAIL_REQUIRED(connect-email): 입력받은 email로 발송
     *
     * ⚠️ 중요한 원칙
     * 1) rawToken은 DB에 저장하지 않는다.
     * 2) rawToken은 "메일 링크"에만 포함된다.
     * 3) 링크는 무조건 백엔드 verify 엔드포인트로 보낸다.
     *    (verify 성공 후 프론트 redirect는 EmailVerificationService가 담당)
     *
     * @param toEmail  실제 수신자 이메일
     * @param rawToken 메일 링크에 포함될 raw 토큰(원문). DB에는 hash만 저장됨.
     */
    public void sendEmailVerification(String toEmail, String rawToken) {

        // -----------------------------
        // 1) 파라미터 검증
        // -----------------------------
        // 이메일이 비어있으면 메일을 보낼 수 없으므로 즉시 실패 처리한다.
        if (!StringUtils.hasText(toEmail)) {
            throw new IllegalArgumentException("toEmail is required for email verification mail");
        }

        // rawToken이 비어있으면 링크를 만들 수 없으므로 즉시 실패 처리한다.
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalArgumentException("rawToken is required for email verification mail");
        }

        // -----------------------------
        // 2) 백엔드 verify 링크 생성
        // -----------------------------
        // rawToken은 URL 쿼리스트링에 들어가므로, 안전하게 URL 인코딩한다.
        // (Base64 URL-safe 토큰이면 대부분 문제 없지만, 정책상 인코딩은 필수로 두는 게 안전)
        String encodedToken = UriUtils.encode(rawToken, StandardCharsets.UTF_8);

        // ✅ 반드시 백엔드 verify API로 링크를 만든다.
        // 클릭하면:
        //   GET {BACKEND}/api/auth/email/verify?token=...
        // 그리고 verify가 성공하면:
        //   서버가 {FRONTEND}/email-verified?code=... 로 redirect 하게 될 것
        String verifyUrl = backendBaseUrl + "/api/auth/email/verify?token=" + encodedToken;

        // -----------------------------
        // 3) 메일 메시지 구성 (HTML 메일)
        // -----------------------------
        // 네이버 메일 등 일부 클라이언트에서 text/plain 링크가 클릭되지 않는 문제가 있어서
        // HTML 메일로 보내 버튼/링크 클릭이 확실히 되도록 한다.

        // 제목
        String subject = "[Stock Dashboard] 이메일 인증을 완료해주세요";

        // 본문
        // - 버튼 클릭 유도
        // - 만료 시간 안내 (실제 TTL은 서버 설정/DB expiresAt 기준)
        // - 버튼이 안 되면 링크 복사/붙여넣기 안내
        String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                  <p>아래 버튼을 클릭하여 이메일 인증을 완료해주세요.</p>

                  <p>
                    <a href="%s"
                       style="display:inline-block;
                              padding:10px 16px;
                              border-radius:10px;
                              background:#2563eb;
                              color:#ffffff;
                              text-decoration:none;">
                      이메일 인증하기
                    </a>
                  </p>

                  <p style="color:#6b7280; font-size:12px;">
                    버튼이 동작하지 않으면 아래 링크를 복사해서 브라우저에 붙여넣어주세요.
                  </p>

                  <p style="word-break: break-all; font-size:12px;">
                    %s
                  </p>

                  <p style="color:#6b7280; font-size:12px;">
                    이 링크는 일정 시간이 지나면 만료됩니다.
                  </p>
                </div>
                """.formatted(verifyUrl, verifyUrl);

        // -----------------------------
        // 4) 메일 전송
        // -----------------------------
        // ✅ (변경) JavaMailSender(SMTP) → Resend API(HTTP)로 전송
        // - Render에서 SMTP 연결 실패(timeout) 문제를 제거하기 위함
        try {
            resendEmailClient.sendHtml(toEmail, subject, html);
        } catch (Exception e) {
            // 메일 전송 실패는 가입/연결 플로우에 직접 영향을 주므로 로그를 남기고 예외를 던진다.
            log.error("[EMAIL] verification mail send failed to={}", toEmail, e);
            throw new IllegalStateException("Failed to send verification email", e);
        }

        // -----------------------------
        // 5) 로깅 (추적 목적)
        // -----------------------------
        // - 운영환경에서는 verifyUrl 전체를 로그에 남기지 않는 것을 권장
        //   (토큰이 포함되어 있기 때문)
        // - 지금은 개발 단계이므로 남겨도 되지만, 배포 시 마스킹 추천
        log.info("[EMAIL] verification mail sent to={}, verifyUrl={}", toEmail, verifyUrl);
    }
}
