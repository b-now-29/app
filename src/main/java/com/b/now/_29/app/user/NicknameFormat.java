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

/** {@link Nickname} 규칙과 같은 검증. null도 위반이다. */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NicknameFormat.Validator.class)
public @interface NicknameFormat {

    String message() default Nickname.RULE_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NicknameFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return Nickname.isValid(value);
        }
    }
}
