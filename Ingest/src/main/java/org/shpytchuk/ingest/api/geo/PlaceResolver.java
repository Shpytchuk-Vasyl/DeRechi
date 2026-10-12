package org.shpytchuk.ingest.api.geo;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.SourceSettings;

import java.util.Optional;

public interface PlaceResolver {

    /**
     *
     * @throws StageUnavailableException no API key configured
     * @throws IngestException           retries exhausted
     */
    Optional<ResolvedPlace> resolve(@Nullable String placeText, @Nullable String city, SourceSettings chat);
}
