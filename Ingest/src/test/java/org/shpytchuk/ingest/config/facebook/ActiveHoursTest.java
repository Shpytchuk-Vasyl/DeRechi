package org.shpytchuk.ingest.config.facebook;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveHoursTest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    @Test
    void anEmptyValueMeansAllDay() {
        ActiveHours hours = ActiveHours.parse("p", " ");

        assertThat(hours.always()).isTrue();
        assertThat(hours.contains(LocalTime.of(3, 0))).isTrue();
    }

    @Test
    void aDaytimeRangeIncludesItsStartAndExcludesItsEnd() {
        ActiveHours hours = ActiveHours.parse("p", "7:00 - 22:00");

        assertThat(hours.contains(LocalTime.of(6, 59))).isFalse();
        assertThat(hours.contains(LocalTime.of(7, 0))).isTrue();
        assertThat(hours.contains(LocalTime.of(21, 59))).isTrue();
        assertThat(hours.contains(LocalTime.of(22, 0))).isFalse();
    }

    @Test
    void aRangeCanCrossMidnight() {
        ActiveHours hours = ActiveHours.parse("p", "22:00-02:00");

        assertThat(hours.contains(LocalTime.of(23, 30))).isTrue();
        assertThat(hours.contains(LocalTime.of(1, 0))).isTrue();
        assertThat(hours.contains(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void theNextOpeningIsTodayBeforeTheStartAndTomorrowAfterIt() {
        ActiveHours hours = ActiveHours.parse("p", "07:00-22:00");

        assertThat(hours.nextOpening(ZonedDateTime.of(2026, 10, 14, 5, 0, 0, 0, KYIV)))
                .isEqualTo(ZonedDateTime.of(2026, 10, 14, 7, 0, 0, 0, KYIV));
        assertThat(hours.nextOpening(ZonedDateTime.of(2026, 10, 14, 23, 0, 0, 0, KYIV)))
                .isEqualTo(ZonedDateTime.of(2026, 10, 15, 7, 0, 0, 0, KYIV));
    }

    @Test
    void aMalformedValueFailsTheStart() {
        assertThatThrownBy(() -> ActiveHours.parse("derechi.facebook.active-hours", "7-22"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("07:00-22:00");
        assertThatThrownBy(() -> ActiveHours.parse("derechi.facebook.active-hours", "07:00-25:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
