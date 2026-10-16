package org.shpytchuk.clientapi.anotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.shpytchuk.clientapi.validator.PhoneOrNickNameValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {PhoneOrNickNameValidator.class})
public @interface PhoneOrNickName {

    String message() default "A contact without a phone needs a nickName and exactly one socialMedias entry";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
