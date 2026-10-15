package org.shpytchuk.ingest.exception;

import org.shpytchuk.ingest.model.Outcome;

import java.time.Instant;
import java.util.Optional;

public class StageUnavailableException extends RuntimeException {

    public StageUnavailableException(String message) {
        super(message);
    }

    public Optional<Instant> retryAt() {
        return Optional.empty();
    }

    public Outcome outcome() {
        return Outcome.UNAVAILABLE;
    }
}
