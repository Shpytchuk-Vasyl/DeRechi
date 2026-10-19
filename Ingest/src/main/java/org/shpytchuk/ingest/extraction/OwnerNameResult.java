package org.shpytchuk.ingest.extraction;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;

public record OwnerNameResult(OwnerName answer, String model, @Nullable BigDecimal cost, Duration duration) {
}
