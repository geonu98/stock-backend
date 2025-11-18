package com.stock.dashboard.backend.model.payload.request;

import com.stock.dashboard.backend.model.payload.DeviceInfo;
import com.stock.dashboard.backend.validation.annotation.NullOrNotBlank;
import jakarta.validation.Valid; // Jakarta EE 변경
import jakarta.validation.constraints.NotNull; // Jakarta EE 변경
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor; // 기본 생성자 추가
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor // Lombok의 @NoArgsConstructor 추가
@AllArgsConstructor
public class LoginRequest {

    // 아이디 대신 Email을 사용한다고 가정하여 필드명을 Email로 변경했습니다.
    @NotNull(message = "이메일은 필수 항목입니다.") // 아이디 대신 이메일 사용 시 @NullOrNotBlank 대신 @NotNull 사용
    private String email;

    @NotNull(message = "비밀번호는 필수 항목입니다.")
    private String password;

    @Valid
    @NotNull(message = "장치정보는 필수 항목입니다.")
    private DeviceInfo deviceInfo;
}