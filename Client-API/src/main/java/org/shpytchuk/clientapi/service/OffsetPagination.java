package org.shpytchuk.clientapi.service;

import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.query.ScrollSubrange;

import java.util.function.Function;

final class OffsetPagination {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private OffsetPagination() {
    }

    static PageRequest pageRequest(ScrollSubrange subrange, Sort sort) {
        int size = size(subrange);
        return PageRequest.of((int) (offset(subrange) / size), size, sort);
    }

    static <T, R> Window<R> window(Page<T> page, Function<T, R> mapper) {
        return Window.from(
                page.getContent().stream().map(mapper).toList(),
                OffsetScrollPosition.positionFunction(page.getPageable().getOffset()),
                page.hasNext());
    }

    private static int size(ScrollSubrange subrange) {
        int size = subrange.count().orElse(DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("first має бути в межах 1.." + MAX_SIZE);
        }
        return size;
    }

    private static long offset(ScrollSubrange subrange) {
        ScrollPosition position = subrange.position().orElse(null);
        if (position == null || position.isInitial()) {
            return 0;
        }
        if (!(position instanceof OffsetScrollPosition offsetPosition)) {
            throw new IllegalArgumentException("Непідтримуваний курсор");
        }
        return offsetPosition.getOffset() + 1;
    }
}
