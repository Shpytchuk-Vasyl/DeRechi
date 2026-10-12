package org.shpytchuk.ingest.geo;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.PlacesProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.http.HttpCalls;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class GooglePlaceResolver extends TextSearchPlaceResolver {

    private static final String OPERATION = "Google Places search";
    private static final String FIELD_MASK = "places.id,places.displayName,places.location,places.addressComponents";

    private final RestClient restClient;
    private final RetryTemplate retry;

    public GooglePlaceResolver(RestClient.Builder builder, PlacesProperties properties) {
        super(properties);
        this.restClient = builder
                .baseUrl(properties.url())
                .defaultHeader("X-Goog-Api-Key", properties.apiKey())
                .defaultHeader("X-Goog-FieldMask", FIELD_MASK)
                .build();
        this.retry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(), HttpCalls::transientFailure);
    }

    @Override
    protected Optional<ResolvedPlace> search(String query, SourceSettings chat) {
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
        Place first = response.places().getFirst();
        if (first.location() == null) {
            return Optional.empty();
        }
        String name = first.displayName() == null ? null : first.displayName().text();
        return place(first.id(), name, first.location().latitude(), first.location().longitude(), first.country());
    }

    private record SearchRequest(String textQuery, String languageCode, String regionCode, int pageSize) {
    }

    private record SearchResponse(@Nullable List<Place> places) {
    }

    private record Place(@Nullable String id, @Nullable LocalizedText displayName, @Nullable LatLng location,
                         @Nullable List<AddressComponent> addressComponents) {

        @Nullable String country() {
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
