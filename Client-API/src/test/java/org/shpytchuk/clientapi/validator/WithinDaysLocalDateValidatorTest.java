package org.shpytchuk.clientapi.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.anotation.WithinDays;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WithinDaysLocalDateValidatorTest {

    private static final class Holder {
        @WithinDays(30)
        LocalDate date;
    }

    private final WithinDaysLocalDateValidator validator = new WithinDaysLocalDateValidator();

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        validator.initialize(Holder.class.getDeclaredField("date").getAnnotation(WithinDays.class));
    }

    @Test
    void acceptsNullSoThatNotNullOwnsTheMessage() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void acceptsToday() {
        assertThat(validator.isValid(LocalDate.now(), null)).isTrue();
    }

    @Test
    void acceptsExactlyThirtyDaysAgo() {
        assertThat(validator.isValid(LocalDate.now().minusDays(30), null)).isTrue();
    }

    @Test
    void rejectsThirtyOneDaysAgo() {
        assertThat(validator.isValid(LocalDate.now().minusDays(31), null)).isFalse();
    }

    @Test
    void rejectsTomorrow() {
        assertThat(validator.isValid(LocalDate.now().plusDays(1), null)).isFalse();
    }
}
