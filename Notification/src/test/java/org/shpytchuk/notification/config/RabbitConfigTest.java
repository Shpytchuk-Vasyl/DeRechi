package org.shpytchuk.notification.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin-API and Worker send their own copies of the event; only the {@code NOTIFICATION} type id and the JSON
 * shape tie them to this module. The producers pin the same id in their own {@code RabbitConfigTest}.
 */
class RabbitConfigTest {

    private final MessageConverter converter = new RabbitConfig().jsonMessageConverter(JsonMapper.builder().build());

    @Test
    void readsANotificationSentByAnotherModuleByItsTypeId() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "NOTIFICATION");
        String json = """
                {"subject":"Знахідка","message":"Схоже, ми знайшли вашу річ","phone":"+380501234567",
                 "email":"user@derechi.local","socialMedias":["TELEGRAM","VIBER"],"deduplicationKey":"match:1:2"}
                """;

        Object event = converter.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));

        assertThat(event).usingRecursiveComparison().isEqualTo(new NotificationRequestedEvent(
                "Знахідка", "Схоже, ми знайшли вашу річ", "+380501234567", "user@derechi.local",
                new SocialMediaEnum[]{SocialMediaEnum.TELEGRAM, SocialMediaEnum.VIBER}, "match:1:2"));
    }

    @Test
    void readsAnEventWithoutContactsTheProducerLeftOut() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "NOTIFICATION");
        String json = "{\"subject\":\"s\",\"message\":\"m\",\"phone\":null,\"email\":\"user@derechi.local\",\"socialMedias\":[]}";

        Object event = converter.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));

        assertThat(event).isInstanceOfSatisfying(NotificationRequestedEvent.class, notification -> {
            assertThat(notification.phone()).isNull();
            assertThat(notification.deduplicationKey()).isNull();
            assertThat(notification.email()).isEqualTo("user@derechi.local");
        });
    }
}
