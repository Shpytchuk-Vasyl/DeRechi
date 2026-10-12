package org.shpytchuk.ingest.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.PlacesProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.http.HttpCalls;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class GoogleLegacyPlaceResolver extends TextSearchPlaceResolver {

    private static final String OPERATION = "Google Places (legacy) search";
    private static final String OK = "OK";
    private static final String ZERO_RESULTS = "ZERO_RESULTS";
    private static final Pattern ADDRESS_PARTS = Pattern.compile("\\s*,\\s*");

    private final RestClient restClient;
    private final RetryTemplate retry;
    private final String apiKey;

    public GoogleLegacyPlaceResolver(RestClient.Builder builder, PlacesProperties properties) {
        super(properties);
        this.restClient = builder.baseUrl(properties.url()).build();
        this.retry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(), HttpCalls::transientFailure);
        this.apiKey = properties.apiKey();
    }

    @Override
    protected Optional<ResolvedPlace> search(String query, SourceSettings chat) {
        SearchResponse response;
        try {
            response = HttpCalls.execute(retry, OPERATION, () -> restClient.get()
                    .uri(uri -> uri.path("/textsearch/json")
                            .queryParam("query", "{query}")
                            .queryParam("language", "{language}")
                            .queryParam("region", "{region}")
                            .queryParam("key", "{key}")
                            .build(query, chat.locale(), chat.region().toLowerCase(Locale.ROOT), apiKey))
                    .retrieve()
                    .body(SearchResponse.class));
        } catch (RestClientException e) {
            throw new IngestException("Google Places (legacy) failed: " + HttpCalls.describe(e));
        }
        if (response == null || ZERO_RESULTS.equals(response.status())) {
            return Optional.empty();
        }
        if (!OK.equals(response.status())) {
            throw new IngestException("Google Places (legacy) answered " + response.status());
        }
        if (response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }
        Result first = response.results().getFirst();
        if (first.geometry() == null || first.geometry().location() == null) {
            return Optional.empty();
        }
        String country = country(first.formattedAddress(), chat.locale()).orElse(chat.region());
        return place(first.placeId(), first.name(), first.geometry().location().lat(), first.geometry().location().lng(), country);
    }

    static Optional<String> country(@Nullable String formattedAddress, String language) {
        if (formattedAddress == null) {
            return Optional.empty();
        }
        List<String> parts = Arrays.asList(ADDRESS_PARTS.split(formattedAddress.strip()));
        Locale locale = Locale.forLanguageTag(language);
        for (String part : parts.reversed()) {
            Optional<String> code = Arrays.stream(Locale.getISOCountries())
                    .filter(iso -> named(iso, part, locale))
                    .findFirst();
            if (code.isPresent()) {
                return code;
            }
        }
        return Optional.empty();
    }

    private static boolean named(String iso, String name, Locale language) {
        Locale country = Locale.of("", iso);
        return name.equalsIgnoreCase(country.getDisplayCountry(language))
                || name.equalsIgnoreCase(country.getDisplayCountry(Locale.ENGLISH));
    }

    private record SearchResponse(@Nullable String status, @Nullable List<Result> results) {
    }

    private record Result(@JsonProperty("place_id") @Nullable String placeId, @Nullable String name,
                          @JsonProperty("formatted_address") @Nullable String formattedAddress,
                          @Nullable Geometry geometry) {
    }

    private record Geometry(@Nullable LatLng location) {
    }

    private record LatLng(double lat, double lng) {
    }
}
