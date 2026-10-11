package org.shpytchuk.telegramingest.geo;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.telegram.ChatSettings;

import java.util.Optional;

public interface PlaceResolver {

    /**
     *
     * @throws StageUnavailableException no API key configured
     * @throws IngestException           retries exhausted
     */
    Optional<ResolvedPlace> resolve(@Nullable String placeText, @Nullable String city, ChatSettings chat);
}
