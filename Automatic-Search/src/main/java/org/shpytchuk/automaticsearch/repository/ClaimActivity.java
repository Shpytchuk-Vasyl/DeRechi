package org.shpytchuk.automaticsearch.repository;

import java.time.Instant;

public record ClaimActivity(Long itemId, Instant lastClaimAt) {
}
