package org.shpytchuk.ingest.api.ai.model;

import org.jspecify.annotations.Nullable;

public record Money(int amount, @Nullable String currency) {
}
