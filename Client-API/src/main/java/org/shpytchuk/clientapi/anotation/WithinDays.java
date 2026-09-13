package org.shpytchuk.clientapi.anotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.shpytchuk.clientapi.validator.WithinDaysLocalDateValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Repeatable(WithinDays.List.class)
@Documented
@Constraint(validatedBy = {WithinDaysLocalDateValidator.class})
public @interface WithinDays {

    int value();

    String message() default "Date must be within the last {value} days";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
    @Retention(RUNTIME)
    @Documented
    @interface List {

        WithinDays[] value();
    }
}

