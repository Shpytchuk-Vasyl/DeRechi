package org.shpytchuk.clientapi.input;

import java.time.LocalDate;

public record ItemFilterInput(
        String search,
        Long categoryId,
        Long placeId,
        LocalDate dateFrom,
        LocalDate dateTo
) {
    public static final ItemFilterInput EMPTY =
            new ItemFilterInput(null, null, null, null, null);
}

