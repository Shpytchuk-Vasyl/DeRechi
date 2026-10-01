package org.shpytchuk.clientapi.input;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MoneyInput(
        @NotNull @PositiveOrZero Integer amount,
        // ISO 4217; null means "the currency of the place's country"
        @Size(min = 3, max = 3) String currency
) {
}
