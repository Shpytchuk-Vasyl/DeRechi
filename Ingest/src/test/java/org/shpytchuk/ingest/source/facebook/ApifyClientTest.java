package org.shpytchuk.ingest.source.facebook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.facebook.ApifyProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApifyClientTest {

    private static final String RUN = "/acts/apify~facebook-groups-scraper/run-sync-get-dataset-items";

    private HttpServer server;
    private ApifyClient client;
    private int responseStatus = 201;
    private String responseBody = ApifyFixtures.ITEMS;
    private URI requestUri;
    private String authorization;
    private String requestBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/acts/", this::answer);
        server.start();
        client = new ApifyClient(new ApifyProperties("http://127.0.0.1:" + server.getAddress().getPort(), "apify_api_1",
                null, 10, new BigDecimal("0.05"), Duration.ofSeconds(30)));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void runsTheActorForTheGroupsPostsSinceTheGivenTime() {
        client.groupPosts("zzter", Instant.parse("2026-10-13T08:30:00.123Z"));

        assertThat(requestUri.getPath()).isEqualTo(RUN);
        assertThat(requestUri.getQuery()).isEqualTo("timeout=30&maxTotalChargeUsd=0.05&clean=true");
        assertThat(authorization).isEqualTo("Bearer apify_api_1");
        JsonNode input = JsonMapper.builder().build().readTree(requestBody);
        assertThat(input.path("startUrls").path(0).path("url").asString())
                .isEqualTo("https://www.facebook.com/groups/zzter/");
        assertThat(input.path("resultsLimit").asInt()).isEqualTo(10);
        assertThat(input.path("viewOption").asString()).isEqualTo("CHRONOLOGICAL");
        assertThat(input.path("onlyPostsNewerThan").asString()).isEqualTo("2026-10-13T08:30:00");
    }

    @Test
    void readsThePostsOfTheDataset() {
        assertThat(client.groupPosts("zzter", Instant.EPOCH)).isEqualTo(ApifyFixtures.posts());
    }

    @Test
    void aRejectedTokenOrCreditMakesFacebookUnavailable() {
        responseStatus = 402;
        responseBody = "{\"error\": {\"type\": \"not-enough-usage-to-run-paid-actor\"}}";

        assertThatThrownBy(() -> client.groupPosts("zzter", Instant.EPOCH))
                .isInstanceOf(StageUnavailableException.class)
                .hasMessageContaining("HTTP 402");
    }

    @Test
    void anyOtherFailureFailsTheGroup() {
        responseStatus = 408;
        responseBody = "{\"error\": {\"type\": \"run-timeout-exceeded\"}}";

        assertThatThrownBy(() -> client.groupPosts("zzter", Instant.EPOCH))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("HTTP 408");
    }

    @Test
    void anAnswerThatIsNotAListFails() {
        responseBody = "{\"error\": {\"type\": \"run-failed\"}}";

        assertThatThrownBy(() -> client.groupPosts("zzter", Instant.EPOCH)).isInstanceOf(IngestException.class);
    }

    private void answer(HttpExchange exchange) throws IOException {
        requestUri = exchange.getRequestURI();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
