package org.shpytchuk.ingest.event;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

@EventType("ITEM_CREATED")
public record ItemCreatedEvent(long id, LocalDate date, @Nullable String categoryKey, String placeName, String title,
                               @Nullable String description, @Nullable Integer compensation,
                               @Nullable String currency, @Nullable String image) {
}
