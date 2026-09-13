package org.shpytchuk.clientapi.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.shpytchuk.clientapi.anotation.WithinDays;

import java.time.LocalDate;

public class WithinDaysLocalDateValidator
        implements ConstraintValidator<WithinDays, LocalDate> {

    private int days;

    @Override
    public void initialize(WithinDays annotation) {
        this.days = annotation.value();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        LocalDate today = LocalDate.now();
        LocalDate minDate = today.minusDays(days);

        return !value.isBefore(minDate)
                && !value.isAfter(today);
    }
}

