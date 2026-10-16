package org.shpytchuk.ingest.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.shpytchuk.ingest.config.client.ApiProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.RejectedItemException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class GraphQlDeRechiApiTest {

    private static final String URL = "http://gateway/graphql";
    private static final String PHONE = "+380671234567";

    private MockRestServiceServer server;
    private GraphQlDeRechiApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClient = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(restClient).build();
        ApiProperties properties = new ApiProperties("http://gateway", Duration.ofSeconds(5), 3, Duration.ofMillis(1));
        api = new GraphQlDeRechiApi(HttpSyncGraphQlClient.builder(restClient).build(), properties);
    }

    @Test
    void createsLostItemWithTheInputVariables() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.query").value(containsString("createLostItem(input: $input)")))
                .andExpect(jsonPath("$.operationName").value("CreateLostItem"))
                .andExpect(jsonPath("$.variables.input.title").value("Чорний гаманець"))
                .andExpect(jsonPath("$.variables.input.description").value("Біля входу"))
                .andExpect(jsonPath("$.variables.input.date").value("2026-10-09"))
                .andExpect(jsonPath("$.variables.input.compensation.amount").value(500))
                .andExpect(jsonPath("$.variables.input.compensation.currency").value("UAH"))
                .andExpect(jsonPath("$.variables.input.image").value("items/2026/10/a.jpg"))
                .andExpect(jsonPath("$.variables.input.categoryId").value("7"))
                .andExpect(jsonPath("$.variables.input.place.id").value("ChIJ1"))
                .andExpect(jsonPath("$.variables.input.place.name").value("Ocean Plaza"))
                .andExpect(jsonPath("$.variables.input.place.lat").value(50.41))
                .andExpect(jsonPath("$.variables.input.place.lon").value(30.52))
                .andExpect(jsonPath("$.variables.input.place.countryCode").value("UA"))
                .andExpect(jsonPath("$.variables.input.contact.phone").value(PHONE))
                .andExpect(jsonPath("$.variables.input.contact.email").doesNotExist())
                .andExpect(jsonPath("$.variables.input.contact.socialMedias").value(contains("TELEGRAM", "VIBER")))
                .andExpect(content().string(containsString("\"nickName\":null")))
                .andExpect(content().string(containsString("\"profileUrl\":null")))
                .andRespond(withSuccess("{\"data\":{\"createLostItem\":{\"id\":\"42\"}}}", MediaType.APPLICATION_JSON));

        long id = api.create(item(ItemKind.LOST, new Money(500, "UAH"), "items/2026/10/a.jpg"));

        assertThat(id).isEqualTo(42);
        server.verify();
    }

    @Test
    void createsFoundItemWithNullCompensationAndImageSentExplicitly() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.query").value(containsString("createFoundItem(input: $input)")))
                .andExpect(jsonPath("$.operationName").value("CreateFoundItem"))
                .andExpect(content().string(containsString("\"compensation\":null")))
                .andExpect(content().string(containsString("\"image\":null")))
                .andRespond(withSuccess("{\"data\":{\"createFoundItem\":{\"id\":\"9\"}}}", MediaType.APPLICATION_JSON));

        assertThat(api.create(item(ItemKind.FOUND, null, null))).isEqualTo(9);
        server.verify();
    }

    @Test
    void createsAnItemWithTheAuthorsProfileInsteadOfAPhone() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.variables.input.contact.nickName").value("@finder"))
                .andExpect(jsonPath("$.variables.input.contact.profileUrl").value("https://t.me/finder"))
                .andExpect(jsonPath("$.variables.input.contact.socialMedias").value(contains("TELEGRAM")))
                .andExpect(content().string(containsString("\"phone\":null")))
                .andRespond(withSuccess("{\"data\":{\"createLostItem\":{\"id\":\"43\"}}}", MediaType.APPLICATION_JSON));

        NewItem item = new NewItem(ItemKind.LOST, "Чорний гаманець", null, LocalDate.of(2026, 10, 9), null, null, 7,
                new ResolvedPlace("ChIJ1", "Ocean Plaza", 50.41, 30.52, "UA"), null, "@finder", "https://t.me/finder",
                List.of(SocialMedia.TELEGRAM));

        assertThat(api.create(item)).isEqualTo(43);
        server.verify();
    }

    @Test
    void badRequestIsRejectedWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("""
                        {"data":null,"errors":[{"message":"title: size must be between 0 and 100",
                          "path":["createLostItem"],"extensions":{"classification":"BAD_REQUEST"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null)))
                .isInstanceOf(RejectedItemException.class)
                .hasMessageContaining("title: size must be between 0 and 100")
                .message().doesNotContain(PHONE);
        server.verify();
    }

    @Test
    void notFoundCategoryIsRejectedToo() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("""
                        {"data":null,"errors":[{"message":"Category 7 not found","extensions":{"classification":"NOT_FOUND"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null)))
                .isInstanceOf(RejectedItemException.class)
                .hasMessageContaining("Category 7 not found");
    }

    @Test
    void otherGraphQlErrorsFailWithoutTheirMessages() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("""
                        {"errors":[{"message":"Variable 'input' has an invalid value: +380671234567",
                          "extensions":{"classification":"ValidationError"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null)))
                .isInstanceOf(IngestException.class)
                .isNotInstanceOf(RejectedItemException.class)
                .hasMessageContaining("ValidationError")
                .message().doesNotContain(PHONE);
        server.verify();
    }

    @Test
    void readWithGraphQlErrorsIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("""
                        {"errors":[{"message":"boom","extensions":{"classification":"INTERNAL_ERROR"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.countries()).hasMessageContaining("INTERNAL_ERROR");
        server.verify();
    }

    @Test
    void serviceUnavailableIsRetriedUpToMaxAttemptsThenFails(CapturedOutput output) {
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null)))
                .isExactlyInstanceOf(IngestException.class)
                .hasMessage("createLostItem failed: HTTP 503")
                .hasNoCause();
        assertThat(output.getAll()).contains("Retrying createLostItem").doesNotContain(PHONE);
        server.verify();
    }

    @Test
    void mutationIsNotRetriedAfterATimeoutBecauseTheNoticeMayExist() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null))).isInstanceOf(IngestException.class);
        server.verify();
    }

    @Test
    void mutationIsNotRetriedAfterAGatewayTimeoutEither() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null))).hasMessageContaining("HTTP 504");
        server.verify();
    }

    @Test
    void mutationIsRetriedWhenItCertainlyDidNotArrive() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"data\":{\"createLostItem\":{\"id\":\"5\"}}}", MediaType.APPLICATION_JSON));

        assertThat(api.create(item(ItemKind.LOST, null, null))).isEqualTo(5);
        server.verify();
    }

    @Test
    void readIsRetriedAfterATimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"data\":{\"countries\":[{\"code\":\"UA\"}]}}", MediaType.APPLICATION_JSON));

        assertThat(api.countries()).containsExactly("UA");
        server.verify();
    }

    @Test
    void readIsRetriedAfterAnInternalServerError() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"data\":{\"categories\":[{\"id\":\"3\",\"key\":\"KEYS\"}]}}", MediaType.APPLICATION_JSON));

        assertThat(api.categories()).isEqualTo(Map.of("KEYS", 3L));
        server.verify();
    }

    @Test
    void ioErrorMessageIsNotCopied() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withException(new IOException("boom " + PHONE)));

        assertThatThrownBy(() -> api.create(item(ItemKind.LOST, null, null)))
                .isInstanceOf(IngestException.class)
                .message().doesNotContain(PHONE);
    }

    private static NewItem item(ItemKind kind, Money compensation, String imageKey) {
        return new NewItem(kind, "Чорний гаманець", "Біля входу", LocalDate.of(2026, 10, 9), compensation, imageKey, 7,
                new ResolvedPlace("ChIJ1", "Ocean Plaza", 50.41, 30.52, "UA"), PHONE, null, null,
                List.of(SocialMedia.TELEGRAM, SocialMedia.VIBER));
    }
}
