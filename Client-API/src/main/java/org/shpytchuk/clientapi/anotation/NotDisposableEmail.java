package org.shpytchuk.clientapi.anotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.shpytchuk.clientapi.validator.NotDisposableEmailValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {NotDisposableEmailValidator.class})
public @interface NotDisposableEmail {

    String message() default "Disposable email addresses are not accepted";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
