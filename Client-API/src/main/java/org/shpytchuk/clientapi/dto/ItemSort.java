package org.shpytchuk.clientapi.dto;

import org.springframework.data.domain.Sort;

public enum ItemSort {

    DATE_DESC("date", Sort.Direction.DESC),
    DATE_ASC("date", Sort.Direction.ASC),
    TITLE_DESC("title", Sort.Direction.DESC),
    TITLE_ASC("title", Sort.Direction.ASC);

    private final String field;
    private final Sort.Direction direction;

    ItemSort(String field, Sort.Direction direction) {
        this.field = field;
        this.direction = direction;
    }

    public Sort toSort(Sort.Order defaultOrder) {
        return Sort.by(
                new Sort.Order(direction, field),
                defaultOrder
        );
    }
}

