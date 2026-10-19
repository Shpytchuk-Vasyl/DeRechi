package org.shpytchuk.adminapi.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.event.ArchiveRequestedEvent;
import org.shpytchuk.adminapi.event.ItemDeletedEvent;
import org.shpytchuk.adminapi.event.ItemUpdatedEvent;
import org.shpytchuk.adminapi.event.NotificationRequestedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumers (Notification, Worker) have their own copies of these events and find them by the
 * {@code __TypeId__} header alone. Their {@code RabbitConfigTest}s read the same ids back.
 */
class RabbitConfigTest {

    private final MessageConverter converter = new RabbitConfig().jsonMessageConverter(JsonMapper.builder().build());

    @Test
    void labelsAMatchNotificationWithItsTypeIdNotTheClassName() {
        Message message = converter.toMessage(new NotificationRequestedEvent("Subject", "Body", "SMS", null,
                "owner@example.com", new SocialMediaEnum[]{SocialMediaEnum.TELEGRAM}, "match:1:2"), new MessageProperties());

        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "NOTIFICATION");
        assertThat(body(message))
                .contains("\"smsMessage\":\"SMS\"")
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

    @Test
    void labelsAnUpdatedItemWithItsTypeId() {
        Message message = converter.toMessage(new ItemUpdatedEvent(7L, LocalDate.of(2026, 9, 10), 2L, "WALLET",
                49.84, 24.03, "Rynok Square", "Wallet", "Brown", null, "UAH", null, "admin"), new MessageProperties());

        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "ITEM_UPDATED");
        assertThat(body(message))
                .contains("\"id\":7")
                .contains("\"categoryKey\":\"WALLET\"")
                .contains("\"title\":\"Wallet\"")
                .contains("\"origin\":\"admin\"");
    }

    @Test
    void labelsADeletedItemWithItsTypeId() {
        Message message = converter.toMessage(new ItemDeletedEvent(7L), new MessageProperties());

        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "ITEM_DELETED");
        assertThat(body(message)).isEqualTo("{\"id\":7}");
    }

    private static String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
