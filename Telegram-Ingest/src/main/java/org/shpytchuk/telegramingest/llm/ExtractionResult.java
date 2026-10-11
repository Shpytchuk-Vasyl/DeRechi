package org.shpytchuk.telegramingest.llm;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param json  the raw JSON the model returned, for the DRY_RUN preview only (never logged)
 * @param model the model that answered (the fallback one when the main one failed)
 * @param cost  USD from {@code usage.cost}, null when the response had none
 */
public record ExtractionResult(Extraction extraction, String json, String model,
                               @Nullable BigDecimal cost, Duration duration) {
}
