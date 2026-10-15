package org.shpytchuk.ingest.source.facebook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.facebook.FacebookProperties;
import org.shpytchuk.ingest.exception.IngestException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacebookClientTest {

    private final List<String> requested = new ArrayList<>();
    private HttpServer server;
    private FacebookClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/groups/consent/", exchange -> redirect(exchange,
                "/privacy/consent/?flow=user_cookie_choice_v2&source=pft_user_cookie_choice"));
        server.createContext("/groups/held/", exchange -> redirect(exchange, "/checkpoint/1501092823525282/"));
        server.createContext("/groups/moved/", exchange -> redirect(exchange, "/groups/zzter/"));
        server.createContext("/groups/loop/", exchange -> redirect(exchange, "/groups/loop/"));
        server.createContext("/groups/zzter/", exchange -> answer(exchange, 200, "<title>ЗАГУБИВ ЗНАЙШОВ</title>"));
        server.createContext("/privacy/consent/", exchange -> answer(exchange, 200, "<title>Facebook</title>"));
        server.start();
        client = new FacebookClient(new FacebookProperties("c_user=1; xs=2", "", null, null, null, null, 0, null,
                Duration.ofSeconds(5), null));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aRedirectToTheCookieChoiceStopsBeforeThePageIsAsked() {
        assertThatThrownBy(() -> client.page(url("/groups/consent/")))
                .isInstanceOf(FacebookActionRequiredException.class)
                .hasMessageContaining("privacy/consent");
        assertThat(requested).containsExactly("/groups/consent/");
    }

    @Test
    void aRedirectToACheckpointStopsToo() {
        assertThatThrownBy(() -> client.page(url("/groups/held/")))
                .isInstanceOf(FacebookActionRequiredException.class)
                .hasMessageContaining("checkpoint");
    }

    @Test
    void anOrdinaryRedirectIsFollowed() {
        assertThat(client.page(url("/groups/moved/"))).contains("ЗАГУБИВ ЗНАЙШОВ");
        assertThat(requested).containsExactly("/groups/moved/", "/groups/zzter/");
    }

    @Test
    void endlessRedirectsFail() {
        assertThatThrownBy(() -> client.page(url("/groups/loop/")))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("redirected more than");
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        answer(exchange, 302, "");
    }

    private void answer(HttpExchange exchange, int status, String body) throws IOException {
        requested.add(exchange.getRequestURI().getPath());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
