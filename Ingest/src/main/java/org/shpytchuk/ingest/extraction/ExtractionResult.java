package org.shpytchuk.ingest.extraction;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param model the model that answered (the fallback one when the main one failed)
 * @param cost  USD from {@code usage.cost}, null when the response had none
 */
public record ExtractionResult(Extraction extraction, String model, @Nullable BigDecimal cost, Duration duration) {
}
