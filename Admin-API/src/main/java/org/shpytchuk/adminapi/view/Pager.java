package org.shpytchuk.adminapi.view;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.IntStream;

/**
 * {@code 1 … 4 5 [6] 7 8 … 42}.
 */
public record Pager(int current, int total, List<Integer> numbers) {

    private static final int RADIUS = 2;

    public static Pager of(Page<?> page) {
        int total = page.getTotalPages();
        if (total < 1) {
            return new Pager(0, 0, List.of());
        }

        int last = total - 1;
        int window = 2 * RADIUS;
        int from = Math.max(0, Math.min(page.getNumber() - RADIUS, last - window));
        int to = Math.min(last, Math.max(page.getNumber() + RADIUS, window));

        return new Pager(page.getNumber(), total,
                IntStream.rangeClosed(from, to).boxed().toList());
    }

    public boolean visible() {
        return total > 1;
    }

    public boolean hasPrevious() {
        return current > 0;
    }

    public boolean hasNext() {
        return current < total - 1;
    }

    public int previous() {
        return current - 1;
    }

    public int next() {
        return current + 1;
    }

    public boolean showsFirst() {
        return !numbers.isEmpty() && numbers.getFirst() > 0;
    }

    public boolean showsLast() {
        return !numbers.isEmpty() && numbers.getLast() < total - 1;
    }

    public boolean gapBefore() {
        return !numbers.isEmpty() && numbers.getFirst() > 1;
    }

    public boolean gapAfter() {
        return !numbers.isEmpty() && numbers.getLast() < total - 2;
    }

    public int last() {
        return total - 1;
    }
}
