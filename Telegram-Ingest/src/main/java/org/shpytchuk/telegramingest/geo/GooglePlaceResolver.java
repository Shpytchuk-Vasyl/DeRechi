package org.shpytchuk.telegramingest.geo;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.config.PlacesProperties;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.http.HttpCalls;
import org.shpytchuk.telegramingest.telegram.ChatSettings;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
public class GooglePlaceResolver implements PlaceResolver {

    private static final String OPERATION = "Google Places search";
    private static final String FIELD_MASK = "places.id,places.displayName,places.location,places.addressComponents";
    private static final int NAME_LENGTH = 100;

    private final RestClient restClient;
    private final RetryTemplate retry;
    private final PlacesProperties properties;

    public GooglePlaceResolver(RestClient.Builder builder, PlacesProperties properties) {
        this.restClient = builder
                .baseUrl(properties.url())
                .defaultHeader("X-Goog-Api-Key", properties.apiKey())
                .defaultHeader("X-Goog-FieldMask", FIELD_MASK)
                .build();
        this.retry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(), HttpCalls::transientFailure);
        this.properties = properties;
    }

    @Override
    public Optional<ResolvedPlace> resolve(@Nullable String placeText, @Nullable String city, ChatSettings chat) {
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

    private Optional<ResolvedPlace> search(String query, ChatSettings chat) {
        SearchRequest request = new SearchRequest(query, chat.locale(), chat.region(), 1);
        SearchResponse response;
        try {
            response = HttpCalls.execute(retry, OPERATION, () -> restClient.post()
                    .uri("/places:searchText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SearchResponse.class));
        } catch (RestClientException e) {
            throw new IngestException("Google Places failed: " + HttpCalls.describe(e));
        }
        if (response == null || response.places() == null || response.places().isEmpty()) {
            return Optional.empty();
        }
        return response.places().getFirst().resolved();
    }

    private record SearchRequest(String textQuery, String languageCode, String regionCode, int pageSize) {
    }

    private record SearchResponse(@Nullable List<Place> places) {
    }

    private record Place(@Nullable String id, @Nullable LocalizedText displayName, @Nullable LatLng location,
                         @Nullable List<AddressComponent> addressComponents) {

        Optional<ResolvedPlace> resolved() {
            String name = displayName == null || displayName.text() == null ? "" : displayName.text().strip();
            String country = country();
            if (id == null || location == null || name.isEmpty() || country == null) {
                return Optional.empty();
            }
            if (name.length() > NAME_LENGTH) {
                name = name.substring(0, NAME_LENGTH).strip();
            }
            return Optional.of(new ResolvedPlace(id, name, location.latitude(), location.longitude(), country));
        }

        private @Nullable String country() {
            if (addressComponents == null) {
                return null;
            }
            return addressComponents.stream()
                    .filter(component -> component.types() != null && component.types().contains("country"))
                    .map(AddressComponent::shortText)
                    .filter(StringUtils::hasText)
                    .map(code -> code.strip().toUpperCase(Locale.ROOT))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record LocalizedText(@Nullable String text) {
    }

    private record LatLng(double latitude, double longitude) {
    }

    private record AddressComponent(@Nullable String shortText, @Nullable List<String> types) {
    }
}
