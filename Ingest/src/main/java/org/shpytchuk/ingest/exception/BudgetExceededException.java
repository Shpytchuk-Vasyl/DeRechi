package org.shpytchuk.ingest.exception;

import org.shpytchuk.ingest.model.Outcome;

import java.time.Instant;
import java.util.Optional;

public class BudgetExceededException extends StageUnavailableException {

    private final Instant retryAt;

    public BudgetExceededException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    @Override
    public Optional<Instant> retryAt() {
        return Optional.of(retryAt);
    }

    @Override
    public Outcome outcome() {
        return Outcome.BUDGET;
    }
}
