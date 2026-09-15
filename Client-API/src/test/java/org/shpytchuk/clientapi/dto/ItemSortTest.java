package org.shpytchuk.clientapi.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSortTest {

    @Test
    void appendsDefaultOrderAsTieBreaker() {
        Sort sort = ItemSort.DATE_ASC.toSort(Sort.Order.desc("id"));

        assertThat(sort).containsExactly(Sort.Order.asc("date"), Sort.Order.desc("id"));
    }

    @Test
    void mapsEveryConstantToItsFieldAndDirection() {
        assertThat(first(ItemSort.DATE_DESC)).isEqualTo(Sort.Order.desc("date"));
        assertThat(first(ItemSort.DATE_ASC)).isEqualTo(Sort.Order.asc("date"));
        assertThat(first(ItemSort.TITLE_DESC)).isEqualTo(Sort.Order.desc("title"));
        assertThat(first(ItemSort.TITLE_ASC)).isEqualTo(Sort.Order.asc("title"));
    }

    private static Sort.Order first(ItemSort sort) {
        List<Sort.Order> orders = sort.toSort(Sort.Order.desc("id")).toList();
        return orders.getFirst();
    }
}
