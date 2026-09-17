package org.shpytchuk.adminapi.form;

import org.springframework.format.annotation.DateTimeFormat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;


public record ItemFilter(
        String q,
        Long categoryId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {

    public ItemFilter {
        q = q == null || q.isBlank() ? null : q.strip();

        if (from != null && to != null && from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
    }

    public static ItemFilter empty() {
        return new ItemFilter(null, null, null, null);
    }

    public boolean active() {
        return q != null || categoryId != null || from != null || to != null;
    }

    public String queryString() {
        StringBuilder query = new StringBuilder();
        append(query, "q", q);
        append(query, "categoryId", categoryId);
        append(query, "from", from);
        append(query, "to", to);
        return query.toString();
    }

    private static void append(StringBuilder query, String name, Object value) {
        if (value != null) {
            query.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        }
    }
}
