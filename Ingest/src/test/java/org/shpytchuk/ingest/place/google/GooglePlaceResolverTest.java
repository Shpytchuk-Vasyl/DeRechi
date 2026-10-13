package org.shpytchuk.ingest.place.google;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.places.PlacesProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlaceResolverTest {

    private static final String URL = "https://places.test/v1";
    private static final String SEARCH = URL + "/places:searchText";
    private static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");

    private static final String CROSSROADS = """
            {"places": [{"id": "ChIJcrossroads", "displayName": {"text": "вулиця Наукова", "languageCode": "uk"},
              "location": {"latitude": 49.8125, "longitude": 24.0144},
              "addressComponents": [
                {"longText": "Львів", "shortText": "Львів", "types": ["locality", "political"]},
                {"longText": "Україна", "shortText": "ua", "types": ["country", "political"]}]}]}
            """;
    private static final String CITY = """
            {"places": [{"id": "ChIJlviv", "displayName": {"text": "Львів"},
              "location": {"latitude": 49.8397, "longitude": 24.0297},
              "addressComponents": [{"longText": "Україна", "shortText": "UA", "types": ["country", "political"]}]}]}
            """;

    private MockRestServiceServer server;
    private GooglePlaceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = resolver("places-key");
    }

    private GooglePlaceResolver resolver(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GooglePlaceResolver(builder,
                new PlacesProperties(PlacesProperties.Api.NEW, URL + "/", apiKey, Duration.ofSeconds(5), 3, Duration.ofMillis(1)));
    }

    @Test
    void searchesThePlaceWithTheCityAndTheMinimalFieldMask() {
        server.expect(requestTo(SEARCH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "places-key"))
                .andExpect(header("X-Goog-FieldMask", "places.id,places.displayName,places.location,places.addressComponents"))
                .andExpect(jsonPath("$.textQuery").value("перехрестя Наукова / Кульпарківська, Львів"))
                .andExpect(jsonPath("$.languageCode").value("uk"))
                .andExpect(jsonPath("$.regionCode").value("UA"))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andRespond(withSuccess(CROSSROADS, MediaType.APPLICATION_JSON));

        Optional<ResolvedPlace> place = resolver.resolve("перехрестя Наукова / Кульпарківська", null, LVIV);

        server.verify();
        assertThat(place).contains(new ResolvedPlace("ChIJcrossroads", "вулиця Наукова", 49.8125, 24.0144, "UA"));
    }

    @Test
    void theCityOfThePostWinsOverTheDefaultCity() {
        server.expect(requestTo(SEARCH))
                .andExpect(jsonPath("$.textQuery").value("ТЦ Ocean Plaza, Київ"))
                .andRespond(withSuccess(CROSSROADS, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve("ТЦ Ocean Plaza", "Київ", LVIV)).isPresent();
        server.verify();
    }

    @Test
    void fallsBackToTheCityWhenThePlaceIsNotFound() {
        server.expect(requestTo(SEARCH))
                .andExpect(jsonPath("$.textQuery").value("десь біля зупинки, Львів"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEARCH))
                .andExpect(jsonPath("$.textQuery").value("Львів"))
                .andRespond(withSuccess(CITY, MediaType.APPLICATION_JSON));

        Optional<ResolvedPlace> place = resolver.resolve("десь біля зупинки", null, LVIV);

        server.verify();
        assertThat(place.map(ResolvedPlace::id)).contains("ChIJlviv");
    }

    @Test
    void searchesOnlyTheCityWithoutAPlaceText() {
        server.expect(ExpectedCount.once(), requestTo(SEARCH))
                .andExpect(jsonPath("$.textQuery").value("Львів"))
                .andRespond(withSuccess(CITY, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(" ", null, LVIV)).isPresent();
        server.verify();
    }

    @Test
    void aResultWithoutACountryIsNotFound() {
        String noCountry = CROSSROADS.replace("\"country\", ", "");
        server.expect(ExpectedCount.twice(), requestTo(SEARCH))
                .andRespond(withSuccess(noCountry, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve("Наукова", null, LVIV)).isEmpty();
        server.verify();
    }

    @Test
    void nothingToSearchWithoutAPlaceAndACity() {
        assertThat(resolver.resolve(null, null, new SourceSettings("UA", null, "uk"))).isEmpty();
        server.verify();
    }

    @Test
    void cutsTheNameTo100Characters() {
        server.expect(requestTo(SEARCH))
                .andRespond(withSuccess(CITY.replace("\"Львів\"}", "\"" + "Л".repeat(150) + "\"}"), MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(null, "Львів", LVIV).orElseThrow().name()).hasSize(100);
    }

    @Test
    void retriesA429AndA500() {
        server.expect(requestTo(SEARCH)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(SEARCH)).andRespond(withServerError());
        server.expect(requestTo(SEARCH)).andRespond(withSuccess(CITY, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(null, "Львів", LVIV)).isPresent();
        server.verify();
    }

    @Test
    void failsAfterMaxAttempts() {
        server.expect(ExpectedCount.times(3), requestTo(SEARCH)).andRespond(withServerError());

        assertThatThrownBy(() -> resolver.resolve(null, "Львів", LVIV))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("HTTP 500");
        server.verify();
    }

    @Test
    void doesNotRetryAClientError() {
        server.expect(ExpectedCount.once(), requestTo(SEARCH))
                .andRespond(withBadRequest().body("{\"error\":{\"message\":\"Invalid textQuery Львів\"}}"));

        assertThatThrownBy(() -> resolver.resolve(null, "Львів", LVIV))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining("Львів")
                .hasNoCause();
        server.verify();
    }

    @Test
    void withoutAKeyMakesNoCall() {
        resolver = resolver("");

        assertThatThrownBy(() -> resolver.resolve("Наукова", "Львів", LVIV)).isInstanceOf(StageUnavailableException.class);
        server.verify();
    }
}
