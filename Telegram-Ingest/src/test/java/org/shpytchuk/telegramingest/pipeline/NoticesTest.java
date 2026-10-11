package org.shpytchuk.telegramingest.pipeline;

import org.junit.jupiter.api.Test;
import org.shpytchuk.telegramingest.model.Money;
import org.shpytchuk.telegramingest.model.SocialMedia;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoticesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test
    void cutsAtAWordBoundaryWithAnEllipsis() {
        String text = "Знайдено гаманець ".repeat(20);

        String cut = Notices.cut(text, Notices.DESCRIPTION_MAX);

        assertThat(cut).hasSizeLessThanOrEqualTo(Notices.DESCRIPTION_MAX).endsWith("…").doesNotContain("  ");
        String kept = cut.substring(0, cut.length() - 1);
        assertThat(text).startsWith(kept);
        assertThat(text.charAt(kept.length())).isEqualTo(' ');
    }

    @Test
    void keepsShortTextAndDropsBlank() {
        assertThat(Notices.cut("  Ключі \n від авто ", 100)).isEqualTo("Ключі від авто");
        assertThat(Notices.cut("   ", 100)).isNull();
        assertThat(Notices.cut(null, 100)).isNull();
    }

    @Test
    void cutsAWordLongerThanTheLimit() {
        assertThat(Notices.cut("x".repeat(300), 100)).hasSize(100);
    }

    @Test
    void dateFallsBackToThePostAndNeverLiesInTheFuture() {
        assertThat(Notices.date(null, TODAY.minusDays(1), TODAY, Period.ofDays(30))).isEqualTo(TODAY.minusDays(1));
        assertThat(Notices.date(TODAY.plusDays(2), TODAY, TODAY, Period.ofDays(30))).isEqualTo(TODAY);
    }

    @Test
    void dateOlderThanTheLimitIsRefused() {
        assertThat(Notices.date(TODAY.minusDays(31), TODAY, TODAY, Period.ofDays(30))).isNull();
        assertThat(Notices.date(TODAY.minusDays(30), TODAY, TODAY, Period.ofDays(30))).isEqualTo(TODAY.minusDays(30));
    }

    @Test
    void compensationNeedsAPositiveAmountAndAnIsoCurrency() {
        assertThat(Notices.compensation(new Money(0, "UAH"))).isNull();
        assertThat(Notices.compensation(new Money(500, "uah"))).isEqualTo(new Money(500, "UAH"));
        assertThat(Notices.compensation(new Money(500, "грн"))).isEqualTo(new Money(500, null));
        assertThat(Notices.compensation(null)).isNull();
    }

    @Test
    void addsTelegramWhenThePhoneCameFromTheProfile() {
        assertThat(Notices.socialMedias(List.of(SocialMedia.VIBER), true))
                .containsExactly(SocialMedia.VIBER, SocialMedia.TELEGRAM);
        assertThat(Notices.socialMedias(null, false)).isEmpty();
    }
}
