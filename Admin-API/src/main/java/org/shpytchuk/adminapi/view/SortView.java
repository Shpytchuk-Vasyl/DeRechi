package org.shpytchuk.adminapi.view;

import org.springframework.data.domain.Sort;

public record SortView(String property, boolean ascending) {

    public static SortView of(Sort sort) {
        return sort.stream().findFirst()
                .map(order -> new SortView(order.getProperty(), order.isAscending()))
                .orElse(new SortView("id", false));
    }

    public boolean by(String column) {
        return property.equals(column);
    }

    public String parameter(String column) {
        return column + "," + (by(column) && ascending ? "desc" : "asc");
    }

    public String icon(String column) {
        if (!by(column)) {
            return "fa-sort";
        }
        return ascending ? "fa-sort-up" : "fa-sort-down";
    }

    public String queryString() {
        return "&sort=" + property + "," + (ascending ? "asc" : "desc");
    }
}
