package org.shpytchuk.clientapi.input;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.shpytchuk.clientapi.anotation.WithinDays;

import java.time.LocalDate;

public record ItemInput(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 250) String description,
        @NotNull @PastOrPresent @WithinDays(30) LocalDate date,
        @PositiveOrZero Integer compensation,
        @Size(max = 200) String image,
        @NotNull Long categoryId,
        @NotNull @Valid PlaceInput place,
        @NotNull @Valid ContactInfoInput contact
) {
}
