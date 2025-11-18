package com.stock.dashboard.backend.model.payload.request;

import jakarta.validation.constraints.NotBlank; // Jakarta EE 변경
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePasswordRequest {

    @NotBlank(message = "기존 비밀번호는 필수 항목입니다.")
    private String oldPassword;

    @NotBlank(message = "새 비밀번호는 필수 항목입니다.")
    private String newPassword;
}