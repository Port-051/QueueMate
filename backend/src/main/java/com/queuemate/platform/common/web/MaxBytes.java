package com.queuemate.platform.common.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * 문자열의 UTF-8 바이트 길이 상한. {@code @Size} 는 글자 수를 센다 — 한글은 한 글자가 3바이트라 둘이 다르다.
 *
 * <p>비밀번호에 쓴다 — BCrypt 는 72<b>바이트</b>까지만 보고, Spring Security 의 {@code BCryptPasswordEncoder} 는
 * 그보다 길면 예외를 던진다. 검증에서 먼저 400 으로 거른다. {@code null} 은 통과시킨다({@code @NotNull} 의 몫이다).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxBytes.Validator.class)
public @interface MaxBytes {

    int value();

    String message() default "UTF-8 로 {value}바이트를 넘을 수 없습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxBytes, String> {

        private int max;

        @Override
        public void initialize(MaxBytes annotation)
        {
            this.max = annotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context)
        {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
