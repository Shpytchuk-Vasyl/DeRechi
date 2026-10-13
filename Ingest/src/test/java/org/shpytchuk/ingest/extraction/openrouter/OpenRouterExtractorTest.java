package org.shpytchuk.ingest.extraction.openrouter;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.config.openrouter.OpenRouterProperties;
import org.shpytchuk.ingest.exception.BudgetExceededException;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.extraction.Extraction;
import org.shpytchuk.ingest.extraction.ExtractionRequest;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.extraction.PromptBuilder;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouterExtractorTest {

    private static final String URL = "https://openrouter.test/api/v1";
    private static final String COMPLETIONS = URL + "/chat/completions";
    private static final String MAIN = "google/gemini-3.1-flash-lite";
    private static final String FALLBACK = "qwen/qwen3.5-flash-02-23";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final PromptBuilder PROMPTS = new PromptBuilder(JSON);
    private static final IngestProperties INGEST = new IngestProperties(null, "hash", null, 0, null,
            "https://derechi.test", "uk", 0.6);

    private static final String ANSWER = """
            {"isNotice": true, "kind": "FOUND", "title": "Знайдено Леокарту",
             "description": "Знайдено Леокарту на перехресті.", "category": "DOCUMENTS", "date": "2026-10-08",
             "placeText": "перехрестя Наукова / Кульпарківська", "city": null,
             "compensation": {"amount": 200, "currency": "UAH"}, "contactPhoneRef": "PHONE_1",
             "socialMedias": ["TELEGRAM"], "confidence": 0.9, "rejectReason": null}
            """;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
    private MockRestServiceServer server;
    private OpenRouterExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = extractor(properties("secret-key", FALLBACK, 300));
    }

    private OpenRouterExtractor extractor(OpenRouterProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenRouterExtractor(builder, properties, INGEST, PROMPTS, new DailyCallCounter(clock), JSON);
    }

    private static OpenRouterProperties properties(String apiKey, String fallback, int dailyLimit) {
        return properties(apiKey, fallback, true, dailyLimit);
    }

    private static OpenRouterProperties properties(String apiKey, String fallback, boolean routing, int dailyLimit) {
        return new OpenRouterProperties(URL + "/", apiKey, MAIN, fallback, routing, Duration.ofSeconds(5), 3,
                Duration.ofMillis(1), dailyLimit);
    }

    private static ExtractionRequest request(List<byte[]> photos) {
        return new ExtractionRequest("Знайдено леокарт. [PHONE_1]", LocalDate.parse("2026-10-09"),
                LocalDate.parse("2026-10-10"), "Львів", List.of("DOCUMENTS", "WALLET", "PHONES"), photos);
    }

    private static String response(String content, @Nullable String model, @Nullable Object cost) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 3700);
        if (cost != null) {
            usage.put("cost", cost);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "gen-1");
        if (model != null) {
            root.put("model", model);
        }
        root.put("choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", content))));
        root.put("usage", usage);
        return JSON.writeValueAsString(root);
    }

    @Test
    void sendsAStrictSchemaTheFallbackModelAndThePhotos() {
        byte[] photo = {1, 2, 3};
        server.expect(requestTo(COMPLETIONS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(header("HTTP-Referer", "https://derechi.test"))
                .andExpect(header("X-Title", "DeRechi"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MAIN))
                .andExpect(jsonPath("$.models", contains(MAIN, FALLBACK)))
                .andExpect(jsonPath("$.temperature").value(0))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(PROMPTS.systemPrompt()))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[0].text").value(PROMPTS.userText(request(List.of(photo)))))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value("data:image/jpeg;base64,AQID"))
                .andExpect(jsonPath("$.messages[1].content.length()").value(2))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("notice"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.additionalProperties").value(false))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.category.enum",
                        contains("DOCUMENTS", "WALLET", "PHONES", null)))
                .andExpect(jsonPath("$.provider.require_parameters").value(true))
                .andRespond(withSuccess(response(ANSWER, MAIN, 0.0012), MediaType.APPLICATION_JSON));

        extractor.extract(request(List.of(photo)));

        server.verify();
    }

    @Test
    void sendsNoModelsListWithoutAFallback() {
        extractor = extractor(properties("secret-key", "", 300));
        server.expect(requestTo(COMPLETIONS))
                .andExpect(jsonPath("$.model").value(MAIN))
                .andExpect(jsonPath("$.models").doesNotExist())
                .andExpect(jsonPath("$.messages[1].content.length()").value(1))
                .andRespond(withSuccess(response(ANSWER, MAIN, null), MediaType.APPLICATION_JSON));

        extractor.extract(request(List.of()));

        server.verify();
    }

    @Test
    void sendsNoOpenRouterFieldsWithoutRouting() {
        extractor = extractor(properties("secret-key", FALLBACK, false, 300));
        server.expect(requestTo(COMPLETIONS))
                .andExpect(jsonPath("$.model").value(MAIN))
                .andExpect(jsonPath("$.models").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andRespond(withSuccess(response(ANSWER, MAIN, null), MediaType.APPLICATION_JSON));

        extractor.extract(request(List.of()));

        server.verify();
    }

    @Test
    void readsTheExtractionTheModelAndTheCost() {
        server.expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER, FALLBACK, 0.0012), MediaType.APPLICATION_JSON));

        ExtractionResult result = extractor.extract(request(List.of()));

        assertThat(result.model()).isEqualTo(FALLBACK);
        assertThat(result.cost()).isEqualByComparingTo(new BigDecimal("0.0012"));
        assertThat(result.json()).isEqualTo(ANSWER.strip());
        assertThat(result.duration().isNegative()).isFalse();
        assertThat(result.extraction()).isEqualTo(new Extraction(true, ItemKind.FOUND, "Знайдено Леокарту",
                "Знайдено Леокарту на перехресті.", "DOCUMENTS", LocalDate.parse("2026-10-08"),
                "перехрестя Наукова / Кульпарківська", null, new Money(200, "UAH"), "PHONE_1",
                List.of(SocialMedia.TELEGRAM), 0.9, null));
    }

    @Test
    void costIsNullWhenTheResponseHasNoneAndTheModelFallsBackToTheConfiguredOne() {
        server.expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(response("```json\n" + ANSWER + "```", null, null), MediaType.APPLICATION_JSON));

        ExtractionResult result = extractor.extract(request(List.of()));

        assertThat(result.cost()).isNull();
        assertThat(result.model()).isEqualTo(MAIN);
        assertThat(result.extraction().kind()).isEqualTo(ItemKind.FOUND);
    }

    @Test
    void retriesA429() {
        server.expect(requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER, MAIN, 0.001), MediaType.APPLICATION_JSON));

        ExtractionResult result = extractor.extract(request(List.of()));

        server.verify();
        assertThat(result.extraction().title()).isEqualTo("Знайдено Леокарту");
    }

    @Test
    void retriesATimeout() {
        server.expect(requestTo(COMPLETIONS)).andRespond(withException(new SocketTimeoutException("timed out")));
        server.expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER, MAIN, 0.001), MediaType.APPLICATION_JSON));

        extractor.extract(request(List.of()));

        server.verify();
    }

    @Test
    void failsAfterMaxAttemptsOfServerErrors() {
        server.expect(ExpectedCount.times(3), requestTo(COMPLETIONS))
                .andRespond(withServerError().body("upstream said: secret post text"));

        assertThatThrownBy(() -> extractor.extract(request(List.of())))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageNotContaining("secret post text")
                .hasNoCause();
        server.verify();
    }

    @Test
    void doesNotRetryAClientError() {
        server.expect(ExpectedCount.once(), requestTo(COMPLETIONS))
                .andRespond(withBadRequest().body("{\"error\":{\"message\":\"secret post text\"}}"));

        assertThatThrownBy(() -> extractor.extract(request(List.of())))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining("secret post text")
                .hasNoCause();
        server.verify();
    }

    @Test
    void doesNotRetryAResponseThatIsNotJson() {
        server.expect(ExpectedCount.once(), requestTo(COMPLETIONS))
                .andRespond(withSuccess("<html>secret post text", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(request(List.of())))
                .isInstanceOf(IngestException.class)
                .hasMessageNotContaining("secret post text")
                .hasNoCause();
        server.verify();
    }

    @Test
    void asksOnceMoreAfterContentThatIsNotJsonThenFails() {
        server.expect(ExpectedCount.times(2), requestTo(COMPLETIONS))
                .andRespond(withSuccess(response("Знайдено леокарт, звертайтесь", MAIN, 0.001), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(request(List.of())))
                .isInstanceOf(IngestException.class)
                .hasMessageNotContaining("Знайдено");
        server.verify();
    }

    @Test
    void anUnknownEnumValueIsAnInvalidAnswerAskedForAgain() {
        server.expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER.replace("\"FOUND\"", "\"STOLEN\""), MAIN, 0.001),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER, MAIN, 0.001), MediaType.APPLICATION_JSON));

        ExtractionResult result = extractor.extract(request(List.of()));

        server.verify();
        assertThat(result.extraction().kind()).isEqualTo(ItemKind.FOUND);
    }

    @Test
    void aWrongTypeIsAnInvalidAnswer() {
        server.expect(ExpectedCount.times(2), requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER.replace("0.9", "\"high\""), MAIN, 0.001),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(request(List.of()))).isInstanceOf(IngestException.class);
        server.verify();
    }

    @Test
    void withoutAKeyMakesNoCall() {
        extractor = extractor(properties(" ", FALLBACK, 300));

        assertThatThrownBy(() -> extractor.extract(request(List.of()))).isInstanceOf(StageUnavailableException.class);
        server.verify();
    }

    @Test
    void stopsAtTheDailyLimitAndStartsAgainOnTheNextUtcDay() {
        extractor = extractor(properties("secret-key", FALLBACK, 1));
        server.expect(requestTo(COMPLETIONS)).andRespond(withServerError());
        server.expect(ExpectedCount.times(2), requestTo(COMPLETIONS))
                .andRespond(withSuccess(response(ANSWER, MAIN, 0.001), MediaType.APPLICATION_JSON));

        extractor.extract(request(List.of()));
        assertThatThrownBy(() -> extractor.extract(request(List.of())))
                .isInstanceOfSatisfying(BudgetExceededException.class,
                        e -> assertThat(e.retryAt()).isEqualTo(Instant.parse("2026-10-11T00:00:00Z")));

        clock.set(Instant.parse("2026-10-11T00:00:01Z"));
        extractor.extract(request(List.of()));
        server.verify();
    }

    @Test
    void theRequestBodyIsUtf8() {
        server.expect(requestTo(COMPLETIONS))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request)
                        .getBodyAsString(StandardCharsets.UTF_8)).contains("Знайдено леокарт"))
                .andExpect(jsonPath("$.messages[1].content[0].text", containsString("[PHONE_1]")))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required", hasItem("contactPhoneRef")))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.kind.enum[2]", nullValue()))
                .andRespond(withSuccess(response(ANSWER, MAIN, 0.001), MediaType.APPLICATION_JSON));

        extractor.extract(request(List.of()));

        server.verify();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
