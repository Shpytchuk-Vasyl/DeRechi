package org.shpytchuk.ingest.event;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public interface ItemEvent {

    long id();

    LocalDate date();

    @Nullable String categoryKey();

    String placeName();

    String title();

    @Nullable String description();

    @Nullable Integer compensation();

    @Nullable String currency();

    @Nullable String image();

    @Nullable String origin();
}
