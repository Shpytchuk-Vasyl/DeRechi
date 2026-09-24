package org.shpytchuk.clientapi.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ItemFilterInput(
        @Size(max = 100) String search,
        Long categoryId,
        LocalDate dateFrom,
        LocalDate dateTo,
        @Valid NearInput near
) {
    public static final ItemFilterInput EMPTY =
            new ItemFilterInput(null, null, null, null, null);
}
