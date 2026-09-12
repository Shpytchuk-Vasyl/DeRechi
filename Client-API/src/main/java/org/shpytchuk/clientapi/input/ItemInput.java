package org.shpytchuk.clientapi.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ItemInput(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 250) String description,
        @NotNull LocalDate date,
        @PositiveOrZero Integer compensation,
        @Size(max = 200) String image,
        @NotNull Long categoryId,
        @NotNull Long placeId,
        @NotNull @Valid ContactInfoInput contact
) {
}
