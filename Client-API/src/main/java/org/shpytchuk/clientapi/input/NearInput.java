package org.shpytchuk.clientapi.input;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NearInput(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
        @NotNull @Positive @DecimalMax("500.0") Double radiusKm
) {
    public double radiusMeters() {
        return radiusKm * 1000;
    }
}
