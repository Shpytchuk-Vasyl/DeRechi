package org.shpytchuk.adminapi.form;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ItemFilterTest {

    @Test
    void emptyFilterAddsNothingToPagerLinks() {
        assertThat(ItemFilter.empty().queryString()).isEmpty();
        assertThat(ItemFilter.empty().active()).isFalse();
    }

    @Test
    void encodesTheSearchTextSoItSurvivesPaging() {
        ItemFilter filter = new ItemFilter("синій рюкзак & сумка", 3L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        assertThat(filter.queryString())
                .isEqualTo("&q=%D1%81%D0%B8%D0%BD%D1%96%D0%B9+%D1%80%D1%8E%D0%BA%D0%B7%D0%B0%D0%BA"
                        + "+%26+%D1%81%D1%83%D0%BC%D0%BA%D0%B0&categoryId=3&from=2026-01-01&to=2026-02-01");
    }

    @Test
    void swapsAReversedDateRangeInsteadOfReturningNothing() {
        ItemFilter filter = new ItemFilter(null, null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1));

        assertThat(filter.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filter.to()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void blankSearchIsNotAFilter() {
        assertThat(new ItemFilter("   ", null, null, null).q()).isNull();
        assertThat(new ItemFilter("   ", null, null, null).active()).isFalse();
    }
}
