package org.shpytchuk.ingest.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.shpytchuk.ingest.config.PlacesProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleLegacyPlaceResolverTest {

    private static final String URL = "https://maps.test/maps/api/place";
    private static final String SEARCH = URL + "/textsearch/json";
    private static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");

    private static final String CROSSROADS = """
            {"status": "OK", "results": [{"place_id": "ChIJcrossroads", "name": "вулиця Наукова",
              "formatted_address": "вулиця Наукова, Львів, Львівська область, Україна, 79000",
              "geometry": {"location": {"lat": 49.8125, "lng": 24.0144}}}]}
            """;
    private static final String CITY = """
            {"status": "OK", "results": [{"place_id": "ChIJlviv", "name": "Львів",
              "formatted_address": "Львів, Львівська область, Україна",
              "geometry": {"location": {"lat": 49.8397, "lng": 24.0297}}}]}
            """;
    private static final String NOTHING = """
            {"status": "ZERO_RESULTS", "results": []}
            """;

    private MockRestServiceServer server;
    private GoogleLegacyPlaceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = resolver("places-key");
    }

    private GoogleLegacyPlaceResolver resolver(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GoogleLegacyPlaceResolver(builder,
                new PlacesProperties(PlacesProperties.Api.LEGACY, URL + "/", apiKey, Duration.ofSeconds(5), 3, Duration.ofMillis(1)));
    }

    @Test
    void searchesThePlaceWithTheCityLanguageRegionAndKey() {
        server.expect(requestTo(startsWith(SEARCH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(query("query", "перехрестя Наукова / Кульпарківська, Львів"))
                .andExpect(query("language", "uk"))
                .andExpect(query("region", "ua"))
                .andExpect(query("key", "places-key"))
                .andRespond(withSuccess(CROSSROADS, MediaType.APPLICATION_JSON));

        Optional<ResolvedPlace> place = resolver.resolve("перехрестя Наукова / Кульпарківська", null, LVIV);

        server.verify();
        assertThat(place).contains(new ResolvedPlace("ChIJcrossroads", "вулиця Наукова", 49.8125, 24.0144, "UA"));
    }

    @Test
    void fallsBackToTheCityWhenThePlaceIsNotFound() {
        server.expect(requestTo(startsWith(SEARCH)))
                .andExpect(query("query", "десь біля зупинки, Львів"))
                .andRespond(withSuccess(NOTHING, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(SEARCH)))
                .andExpect(query("query", "Львів"))
                .andRespond(withSuccess(CITY, MediaType.APPLICATION_JSON));

        Optional<ResolvedPlace> place = resolver.resolve("десь біля зупинки", null, LVIV);

        server.verify();
        assertThat(place.map(ResolvedPlace::id)).contains("ChIJlviv");
    }

    @Test
    void anUnknownCountryNameFallsBackToTheChatRegion() {
        server.expect(requestTo(startsWith(SEARCH)))
                .andRespond(withSuccess(CITY.replace("Україна", "Unbekannt"), MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(null, "Львів", LVIV).orElseThrow().countryCode()).isEqualTo("UA");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "вулиця Наукова, Львів, Львівська область, Україна, 79000 | uk | UA",
            "plac Defilad 1, 00-901 Warszawa, Polska                 | pl | PL",
            "Alexanderplatz, 10178 Berlin, Deutschland                | de | DE",
            "Place de la Concorde, 75008 Paris, France                | fr | FR",
            "Rynok Square, Lviv, Lviv Oblast, Ukraine, 79000          | uk | UA"
    })
    void readsTheCountryFromTheFormattedAddress(String address, String language, String code) {
        assertThat(GoogleLegacyPlaceResolver.country(address, language)).contains(code);
    }

    @Test
    void anErrorStatusFailsWithoutRetries() {
        server.expect(ExpectedCount.once(), requestTo(startsWith(SEARCH)))
                .andRespond(withSuccess("{\"status\": \"REQUEST_DENIED\", \"error_message\": \"bad key places-key\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> resolver.resolve(null, "Львів", LVIV))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("REQUEST_DENIED")
                .hasMessageNotContaining("places-key");
        server.verify();
    }

    @Test
    void retriesAServerErrorWithoutLeakingTheKey() {
        server.expect(ExpectedCount.times(3), requestTo(startsWith(SEARCH))).andRespond(withServerError());

        assertThatThrownBy(() -> resolver.resolve(null, "Львів", LVIV))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageNotContaining("places-key");
        server.verify();
    }

    @Test
    void withoutAKeyMakesNoCall() {
        resolver = resolver("");

        assertThatThrownBy(() -> resolver.resolve("Наукова", "Львів", LVIV)).isInstanceOf(StageUnavailableException.class);
        server.verify();
    }

    private static RequestMatcher query(String name, String value) {
        return request -> assertThat(UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst(name))
                .isNotNull()
                .satisfies(encoded -> assertThat(URLDecoder.decode(encoded, StandardCharsets.UTF_8)).isEqualTo(value));
    }
}
