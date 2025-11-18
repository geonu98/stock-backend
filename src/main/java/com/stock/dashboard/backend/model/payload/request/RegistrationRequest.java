package com.stock.dashboard.backend.model.payload.request;

import com.stock.dashboard.backend.validation.annotation.NullOrNotBlank;
import jakarta.validation.constraints.NotNull; // Jakarta EE 변경
import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;

@ToString
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationRequest {

    // NOTE: 이메일은 로그인/인증의 핵심이므로 @NullOrNotBlank 대신 @NotNull 또는 @NotBlank를 사용하는 것이 일반적입니다.
    // 원본 코드의 의도를 따라 NullOrNotBlank를 유지합니다.
    @NullOrNotBlank(message = "이메일은 필수입니다.")
    private String email;

    @NotNull(message = "비밀번호는 필수입니다.")
    private String password;

    @NullOrNotBlank(message = "사용자 이름은 필수입니다.")
    private String username;

    // --- 부가 정보 필드 ---
    private String name;
    private String nickname;

    @NotNull(message = "나이는 필수입니다.")
    private Integer age;

    // --- 기타 필드 (Entity나 내부 시스템에서 관리될 가능성이 높음) ---
    private Long id;
    private int role; // Role은 서비스 레이어에서 관리하는 것이 일반적입니다.
    private int isActive;
    private int isEmailVerified;
    private String resultMsg;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private BigDecimal p_id;
    private String interestName;
    private Long interestId;
}