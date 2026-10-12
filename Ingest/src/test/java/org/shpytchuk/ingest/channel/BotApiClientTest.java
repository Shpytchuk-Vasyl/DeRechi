package org.shpytchuk.ingest.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.shpytchuk.ingest.config.BotProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class BotApiClientTest {

    private static final String TOKEN = "123456:SECRET-bot-token";
    private static final String BASE = "https://api.telegram.org/bot" + TOKEN;
    private static final String OK = "{\"ok\":true,\"result\":{\"message_id\":77,\"chat\":{\"id\":-100}}}";

    private MockRestServiceServer server;
    private BotApiClient bot;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        bot = new BotApiClient(builder, new BotProperties("https://api.telegram.org", TOKEN, "@derechi", "", "uk",
                Duration.ofSeconds(5), 3, Duration.ofMillis(1)));
    }

    @Test
    void sendMessagePostsJsonWithHtmlAndNoLinkPreview() {
        server.expect(requestTo(BASE + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", startsWith(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.chat_id").value("@derechi"))
                .andExpect(jsonPath("$.text").value("<b>hi</b> &amp; bye"))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andExpect(jsonPath("$.link_preview_options.is_disabled").value(true))
                .andRespond(json(OK));

        assertThat(bot.sendMessage("@derechi", "<b>hi</b> &amp; bye")).isEqualTo(77);
        server.verify();
    }

    @Test
    void sendPhotoPostsMultipartWithTheFile() {
        server.expect(requestTo(BASE + "/sendPhoto"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)))
                .andExpect(content().string(containsString("name=\"chat_id\"")))
                .andExpect(content().string(containsString("-100123")))
                .andExpect(content().string(containsString("name=\"photo\"; filename=\"photo.jpg\"")))
                .andExpect(content().string(containsString("Content-Type: image/jpeg")))
                .andExpect(content().string(containsString("name=\"caption\"")))
                .andExpect(content().string(containsString("Підпис")))
                .andExpect(content().string(containsString("name=\"parse_mode\"")))
                .andExpect(content().string(containsString("HTML")))
                .andRespond(json(OK));

        assertThat(bot.sendPhoto("-100123", new byte[]{(byte) 0xFF, (byte) 0xD8, 1}, "Підпис")).isEqualTo(77);
        server.verify();
    }

    @Test
    void tooManyRequestsIsRetried() {
        server.expect(requestTo(BASE + "/sendMessage")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests: retry after 3\"}"));
        server.expect(requestTo(BASE + "/sendMessage")).andRespond(json(OK));

        assertThat(bot.sendMessage("@derechi", "text")).isEqualTo(77);
        server.verify();
    }

    @Test
    void badGatewayIsRetriedUpToMaxAttempts() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>bad gateway</html>"));

        assertThatThrownBy(() -> bot.sendMessage("@derechi", "text"))
                .isInstanceOf(IngestException.class)
                .hasMessage("Bot API sendMessage failed: HTTP 502");
        server.verify();
    }

    @Test
    void internalServerErrorIsNotRetriedBecauseTheMessageMayBePosted() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> bot.sendMessage("@derechi", "text")).hasMessageContaining("HTTP 500");
        server.verify();
    }

    @Test
    void readTimeoutIsNotRetriedBecauseTheMessageMayBePosted() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/sendMessage"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> bot.sendMessage("@derechi", "text")).isInstanceOf(IngestException.class);
        server.verify();
    }

    @Test
    void badRequestFailsAtOnceWithTelegramsDescription() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}"));

        assertThatThrownBy(() -> bot.sendMessage("@nowhere", "text"))
                .isInstanceOf(IngestException.class)
                .hasMessage("Bot API sendMessage failed: HTTP 400 Bad Request: chat not found");
        server.verify();
    }

    @Test
    void successWithoutMessageIdIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/sendMessage")).andRespond(json("{\"ok\":true}"));

        assertThatThrownBy(() -> bot.sendMessage("@derechi", "text")).isInstanceOf(IngestException.class);
        server.verify();
    }

    @Test
    void tokenNeverAppearsInExceptionMessagesOrLogs(CapturedOutput output) {
        server.expect(requestTo(BASE + "/sendPhoto")).andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(BASE + "/sendPhoto"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("upstream " + BASE + " is down"));
        server.expect(requestTo(BASE + "/sendPhoto")).andRespond(withException(new IOException("I/O error on " + BASE)));

        IngestException failure = catchThrowableOfType(IngestException.class,
                () -> bot.sendPhoto("-100123", new byte[]{1}, "caption"));

        assertThat(failure).isNotNull();
        assertThat(failure.getCause()).isNull();
        assertThat(failure.toString()).doesNotContain("SECRET");
        assertThat(output.getAll()).contains("Retrying Bot API sendPhoto").doesNotContain("SECRET");
        server.verify();
    }

    @Test
    void tokenNeverAppearsInTheMessageOfARejection() {
        server.expect(requestTo(BASE + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}"));

        IngestException failure = catchThrowableOfType(IngestException.class, () -> bot.sendMessage("@derechi", "text"));

        assertThat(failure.getMessage()).contains("HTTP 401").doesNotContain("SECRET");
        assertThat(failure.getCause()).isNull();
    }

    private static ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
