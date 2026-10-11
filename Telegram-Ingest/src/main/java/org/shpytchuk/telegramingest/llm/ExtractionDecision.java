package org.shpytchuk.telegramingest.llm;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.model.Outcome;


public record ExtractionDecision(@Nullable Outcome skipOutcome, @Nullable String contactPhone) {

    public static ExtractionDecision skip(Outcome outcome) {
        return new ExtractionDecision(outcome, null);
    }

    public static ExtractionDecision proceed(String contactPhone) {
        return new ExtractionDecision(null, contactPhone);
    }

    public boolean skipped() {
        return skipOutcome != null;
    }
}
