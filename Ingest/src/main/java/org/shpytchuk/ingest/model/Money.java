package org.shpytchuk.ingest.model;

import org.jspecify.annotations.Nullable;

public record Money(int amount, @Nullable String currency) {
}
