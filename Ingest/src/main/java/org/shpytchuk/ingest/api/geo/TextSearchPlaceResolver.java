package org.shpytchuk.ingest.api.geo;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.PlacesProperties;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.util.StringUtils;

import java.util.Optional;

abstract class TextSearchPlaceResolver implements PlaceResolver {

    private static final int NAME_LENGTH = 100;

    private final PlacesProperties properties;

    TextSearchPlaceResolver(PlacesProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<ResolvedPlace> resolve(@Nullable String placeText, @Nullable String city, SourceSettings chat) {
        if (!properties.configured()) {
            throw new StageUnavailableException("Google Places API key is not configured");
        }
        String town = StringUtils.hasText(city) ? city.trim() : chat.defaultCity();
        Optional<ResolvedPlace> place = Optional.empty();
        if (StringUtils.hasText(placeText)) {
            place = search(town == null ? placeText.trim() : placeText.trim() + ", " + town, chat);
        }
        if (place.isEmpty() && town != null) {
            place = search(town, chat);
        }
        return place;
    }

    protected abstract Optional<ResolvedPlace> search(String query, SourceSettings chat);

    protected static Optional<ResolvedPlace> place(@Nullable String id, @Nullable String name, double lat, double lon,
                                                   @Nullable String countryCode) {
        String trimmed = name == null ? "" : name.strip();
        if (id == null || trimmed.isEmpty() || countryCode == null) {
            return Optional.empty();
        }
        if (trimmed.length() > NAME_LENGTH) {
            trimmed = trimmed.substring(0, NAME_LENGTH).strip();
        }
        return Optional.of(new ResolvedPlace(id, trimmed, lat, lon, countryCode));
    }
}
