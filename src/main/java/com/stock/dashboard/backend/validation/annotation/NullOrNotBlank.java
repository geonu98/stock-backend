package com.stock.dashboard.backend.validation.annotation; // 🛑 현재 패키지 유지

import com.stock.dashboard.backend.validation.validator.NullOrNotBlankValidator; // 🛑 Validator 경로 수정

// 🛑 Jakarta EE로 변경
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ElementType.FIELD})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
public @interface NullOrNotBlank {
    // 🛑 메시지 키도 Jakarta EE 기준으로 변경
    String message() default "{jakarta.validation.constraints.Pattern.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}