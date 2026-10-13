package org.shpytchuk.ingest.exception;

import java.time.Instant;

public class BudgetExceededException extends RuntimeException {

    private final Instant retryAt;

    public BudgetExceededException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
