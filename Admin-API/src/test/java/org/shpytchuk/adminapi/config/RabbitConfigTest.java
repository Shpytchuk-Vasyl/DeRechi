package org.shpytchuk.adminapi.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.event.ArchiveRequestedEvent;
import org.shpytchuk.adminapi.event.NotificationRequestedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumers (Notification, Worker) have their own copies of these events and find them by the
 * {@code __TypeId__} header alone. Their {@code RabbitConfigTest}s read the same ids back.
 */
class RabbitConfigTest {

    private final MessageConverter converter = new RabbitConfig().jsonMessageConverter(JsonMapper.builder().build());

    @Test
    void labelsAMatchNotificationWithItsTypeIdNotTheClassName() {
        Message message = converter.toMessage(new NotificationRequestedEvent("Subject", "Body", null,
                "owner@example.com", new SocialMediaEnum[]{SocialMediaEnum.TELEGRAM}, "match:1:2"), new MessageProperties());

        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "NOTIFICATION");
        assertThat(body(message))
                .contains("\"email\":\"owner@example.com\"")
                .contains("\"socialMedias\":[\"TELEGRAM\"]")
                .contains("\"deduplicationKey\":\"match:1:2\"");
    }

    @Test
    void labelsAnArchiveRequestWithItsTypeId() {
        Message message = converter.toMessage(new ArchiveRequestedEvent(5L, "admin@derechi.local"), new MessageProperties());

        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "ARCHIVE_REQUESTED");
        assertThat(body(message)).contains("\"id\":5").contains("\"actor\":\"admin@derechi.local\"");
    }

    private static String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
