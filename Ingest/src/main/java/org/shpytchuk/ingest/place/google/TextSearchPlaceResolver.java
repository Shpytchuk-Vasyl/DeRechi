package org.shpytchuk.ingest.place.google;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.places.PlacesProperties;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.place.PlaceResolver;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

abstract class TextSearchPlaceResolver implements PlaceResolver {

    private static final int NAME_LENGTH = 100;
    private static final Pattern ADDRESS_PARTS = Pattern.compile("\\s*,\\s*");

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

    protected static Optional<ResolvedPlace> place(@Nullable String id, @Nullable String name, @Nullable String address,
                                                   double lat, double lon, @Nullable String countryCode, String language) {
        String trimmed = label(name, withoutCountry(address, countryCode, language));
        if (id == null || trimmed.isEmpty() || countryCode == null) {
            return Optional.empty();
        }
        if (trimmed.length() > NAME_LENGTH) {
            trimmed = trimmed.substring(0, NAME_LENGTH).strip();
        }
        return Optional.of(new ResolvedPlace(id, trimmed, lat, lon, countryCode));
    }

    private static @Nullable String withoutCountry(@Nullable String address, @Nullable String countryCode, String language) {
        if (address == null || countryCode == null) {
            return address;
        }
        Locale country = Locale.of("", countryCode);
        List<String> parts = Arrays.asList(ADDRESS_PARTS.split(address.strip()));
        for (int i = parts.size() - 1; i >= 0; i--) {
            String part = parts.get(i);
            if (part.equalsIgnoreCase(country.getDisplayCountry(Locale.forLanguageTag(language)))
                    || part.equalsIgnoreCase(country.getDisplayCountry(Locale.ENGLISH))) {
                return String.join(", ", parts.subList(0, i));
            }
        }
        return address;
    }

    private static String label(@Nullable String name, @Nullable String address) {
        String title = name == null ? "" : name.strip();
        String full = address == null ? "" : address.strip();
        if (full.isEmpty() || full.startsWith(title)) {
            return full.isEmpty() ? title : full;
        }
        return title.isEmpty() ? full : title + ", " + full;
    }
}
