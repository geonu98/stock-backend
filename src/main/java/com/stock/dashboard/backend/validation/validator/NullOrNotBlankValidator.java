package com.stock.dashboard.backend.validation.validator; // 🛑 경로 유지: validation.validator

import com.stock.dashboard.backend.validation.annotation.NullOrNotBlank; // 🛑 경로 수정: validation.annotation
import jakarta.validation.ConstraintValidator; // 🛑 Jakarta EE로 변경
import jakarta.validation.ConstraintValidatorContext; // 🛑 Jakarta EE로 변경

public class NullOrNotBlankValidator implements ConstraintValidator<NullOrNotBlank, String> {

    @Override
    public void initialize(NullOrNotBlank parameters) {
        //no-op
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (null == value) {
            return true; // null 허용
        }
        // 길이가 0인 문자열은 허용하지 않음 (NotBlank 규칙)
        if (value.length() == 0) {
            return false;
        }

        // 공백 문자열만 있는 경우 검사
        boolean isAllWhitespace = value.matches("^\\s*$");

        // 공백만 있으면 false를 반환합니다. (null이 아니면 공백도 아니어야 함)
        return !isAllWhitespace;
    }
}