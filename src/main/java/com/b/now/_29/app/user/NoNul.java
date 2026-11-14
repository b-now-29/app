package com.b.now._29.app.user;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 비밀번호에 NUL 문자가 없어야 한다 (D11). null은 통과한다. */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoNul.Validator.class)
public @interface NoNul {

    String message() default "NUL 문자를 포함할 수 없습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NoNul, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || !PasswordRule.containsNul(value);
        }
    }
}
