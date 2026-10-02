package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PagesTest {

    @Test
    void keepsWhitelistedOrdersWithTheirDirection() {
        Pageable safe = Pages.safe(PageRequest.of(2, 50, Sort.by(Sort.Order.asc("title"), Sort.Order.desc("date"))));

        assertThat(safe.getPageNumber()).isEqualTo(2);
        assertThat(safe.getPageSize()).isEqualTo(50);
        assertThat(safe.getSort()).containsExactly(Sort.Order.asc("title"), Sort.Order.desc("date"));
    }

    @Test
    void dropsAnythingThatIsNotWhitelistedSoItNeverReachesOrderBy() {
        Pageable safe = Pages.safe(PageRequest.of(0, 20,
                Sort.by(Sort.Order.asc("info.phone"), Sort.Order.desc("date"), Sort.Order.asc("title; drop table place"))));

        assertThat(safe.getSort()).containsExactly(Sort.Order.desc("date"));
    }

    @Test
    void fallsBackToNewestFirstWhenNothingUsableIsLeft() {
        assertThat(Pages.safe(PageRequest.of(0, 20)).getSort()).containsExactly(Sort.Order.desc("id"));
        assertThat(Pages.safe(PageRequest.of(0, 20, Sort.by("info.email"))).getSort())
                .containsExactly(Sort.Order.desc("id"));
    }
}
