package org.shpytchuk.notification.channel;

import io.notifyhub.core.Notification;
import io.notifyhub.core.channel.NotificationChannel;
import io.notifyhub.core.channel.NotificationSendException;
import io.notifyhub.core.channel.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * NotifyHub channel {@code sms} over the SMS-fly REST API v2.4 (action {@code SENDMESSAGE}).
 */
public class SmsFlyChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsFlyChannel.class);

    public static final String NAME = "sms";

    static final String SEND = "SENDMESSAGE";
    static final String ACCEPTED = "ACCEPTD";

    private final RestClient restClient;
    private final SmsFlyProperties properties;

    public SmsFlyChannel(RestClient restClient, SmsFlyProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public static SmsFlyChannel of(RestClient.Builder builder, SmsFlyProperties properties) {
        return new SmsFlyChannel(builder.baseUrl(properties.apiUrl()).build(), properties);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void send(Notification notification) {
        sendWithResult(notification);
    }

    @Override
    public SendResult sendWithResult(Notification notification) {
        Request request = new Request(new Auth(properties.apiKey()), SEND, new SendData(
                recipient(notification.getRecipient()),
                List.of(NAME),
                new Sms(properties.source(), properties.ttlMinutes(), text(notification))));

        JsonNode response = call(request);
        if (response.path("success").asInt() != 1) {
            JsonNode error = response.path("error");
            throw failure("SMS-fly rejected the message: %s %s".formatted(text(error, "code"), text(error, "description")).strip());
        }

        JsonNode data = response.path("data");
        String status = text(data.path(NAME), "status");
        if (!ACCEPTED.equals(status)) {
            throw failure("SMS-fly did not accept the message, status " + (status.isEmpty() ? "missing" : status));
        }

        String messageId = text(data, "messageID");
        log.info("SMS-fly accepted message {}, cost {}", messageId, text(data.path(NAME), "cost"));
        return new SendResult(messageId);
    }

    static String recipient(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw failure("No phone number to send the SMS to");
        }
        return digits;
    }

    private static String text(Notification notification) {
        String text = notification.getRenderedContent();
        if (text == null || text.isBlank()) {
            throw failure("SMS text is empty");
        }
        return text;
    }

    private JsonNode call(Request request) {
        String body;
        try {
            body = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new NotificationSendException(NAME,
                    "SMS-fly answered %d: %s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new NotificationSendException(NAME, "SMS-fly call failed: " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw failure("SMS-fly answered with an empty body");
        }
        try {
            return JsonMapper.shared().readTree(body);
        } catch (JacksonException e) {
            throw new NotificationSendException(NAME, "SMS-fly answered with something other than JSON: " + body, e);
        }
    }

    private static NotificationSendException failure(String message) {
        return new NotificationSendException(NAME, message);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString().strip() : "";
    }

    record Request(Auth auth, String action, SendData data) {
    }

    record Auth(String key) {
    }

    record SendData(String recipient, List<String> channels, Sms sms) {
    }

    record Sms(String source, int ttl, String text) {
    }
}
