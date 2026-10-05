package org.shpytchuk.clientapi.exeption;

import lombok.Getter;

import java.time.Instant;

@Getter
public class UnlockLimitException extends RuntimeException {

    private final Instant retryAfter;

    public UnlockLimitException(Instant retryAfter) {
        super("Unlock limit reached, retry after " + retryAfter);
        this.retryAfter = retryAfter;
    }
}
