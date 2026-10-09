package org.shpytchuk.clientapi.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.anotation.NotDisposableEmail;

@AllArgsConstructor
public class NotDisposableEmailValidator implements ConstraintValidator<NotDisposableEmail, String> {

    private final DisposableEmailDomains domains;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !domains.isDisposable(value);
    }
}
