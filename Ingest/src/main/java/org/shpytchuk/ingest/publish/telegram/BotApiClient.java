package org.shpytchuk.ingest.publish.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.Map;
import java.util.function.Function;

public class BotApiClient {

    private final RestClient restClient;
    private final String token;
    private final RetryTemplate retry;

    public BotApiClient(RestClient.Builder restClient, BotProperties properties) {
        DefaultUriBuilderFactory uris = new DefaultUriBuilderFactory(properties.apiUrl());
        uris.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
        this.restClient = restClient.uriBuilderFactory(uris).build();
        this.token = properties.token();
        this.retry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(), HttpCalls::notDelivered);
    }

    public long sendPhoto(String chat, byte[] jpeg, String caption) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("chat_id", chat);
        body.part("photo", new ByteArrayResource(jpeg)).filename("photo.jpg").contentType(MediaType.IMAGE_JPEG);
        body.part("caption", caption);
        body.part("parse_mode", "HTML");
        return send("sendPhoto", request -> request.contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()));
    }

    public long sendMessage(String chat, String html) {
        Map<String, Object> body = Map.of(
                "chat_id", chat,
                "text", html,
                "parse_mode", "HTML",
                "link_preview_options", Map.of("is_disabled", true));
        return send("sendMessage", request -> request.contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private long send(String method, Function<RestClient.RequestBodySpec, RestClient.RequestHeadersSpec<?>> body) {
        BotResponse response;
        try {
            response = HttpCalls.execute(retry, "Bot API " + method,
                    () -> body.apply(restClient.post().uri("/bot{token}/{method}", token, method))
                            .retrieve()
                            .body(BotResponse.class));
        } catch (RuntimeException e) {
            throw new IngestException("Bot API %s failed: %s%s".formatted(method, HttpCalls.describe(e), description(e)));
        }
        if (response == null || response.result() == null) {
            throw new IngestException("Bot API %s answered without a message id".formatted(method));
        }
        return response.result().messageId();
    }

    private static String description(RuntimeException e) {
        if (!(e instanceof RestClientResponseException response)) {
            return "";
        }
        try {
            BotResponse body = response.getResponseBodyAs(BotResponse.class);
            return body != null && body.description() != null ? " " + body.description() : "";
        } catch (RestClientException notJson) {
            return "";
        }
    }

    private record BotResponse(@Nullable Result result, @Nullable String description) {
    }

    private record Result(@JsonProperty("message_id") long messageId) {
    }
}
