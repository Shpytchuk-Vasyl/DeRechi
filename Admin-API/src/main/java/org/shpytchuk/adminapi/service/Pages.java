package org.shpytchuk.adminapi.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public final class Pages {

    public static final List<String> SORTABLE = List.of("id", "title", "date");

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("id"));

    private Pages() {
    }

    public static Pageable safe(Pageable pageable) {
        List<Sort.Order> allowed = pageable.getSort().stream()
                .filter(order -> SORTABLE.contains(order.getProperty()))
                .toList();

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                allowed.isEmpty() ? NEWEST_FIRST : Sort.by(allowed));
    }
}
