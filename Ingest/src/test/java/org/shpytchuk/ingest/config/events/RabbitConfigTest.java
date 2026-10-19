package org.shpytchuk.ingest.config.events;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.event.ItemArchivedEvent;
import org.shpytchuk.ingest.event.ItemCreatedEvent;
import org.shpytchuk.ingest.event.ItemDeletedEvent;
import org.shpytchuk.ingest.event.ItemUpdatedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConfigTest {

    private final MessageConverter converter = new RabbitConfig().jsonMessageConverter(JsonMapper.builder().build());

    @Test
    void readsACreatedItemWithWhatTheChannelPostNeedsAndIgnoresTheRest() {
        Object event = converter.fromMessage(message("ITEM_CREATED",
                "{\"id\":7,\"date\":\"2026-09-10\",\"category\":2,\"categoryKey\":\"WALLET\",\"lat\":49.84,"
                        + "\"lon\":24.03,\"placeName\":\"Rynok Square\",\"title\":\"Wallet\",\"description\":\"Brown\","
                        + "\"compensation\":500,\"currency\":\"UAH\",\"image\":\"items/2026/09/a.jpg\",\"origin\":\"admin\"}"));

        assertThat(event).isEqualTo(new ItemCreatedEvent(7L, LocalDate.of(2026, 9, 10), "WALLET", "Rynok Square",
                "Wallet", "Brown", 500, "UAH", "items/2026/09/a.jpg", "admin"));
    }

    @Test
    void readsAnUpdatedItemFromAdminApi() {
        Object event = converter.fromMessage(message("ITEM_UPDATED",
                "{\"id\":7,\"date\":\"2026-09-10\",\"category\":2,\"categoryKey\":\"WALLET\",\"lat\":49.84,"
                        + "\"lon\":24.03,\"placeName\":\"Rynok Square\",\"title\":\"Wallet\",\"description\":\"Brown\","
                        + "\"compensation\":null,\"currency\":\"UAH\",\"image\":null,\"origin\":\"admin\"}"));

        assertThat(event).isEqualTo(new ItemUpdatedEvent(7L, LocalDate.of(2026, 9, 10), "WALLET", "Rynok Square",
                "Wallet", "Brown", null, "UAH", null, "admin"));
    }

    @Test
    void readsAnArchivedItemFromWorker() {
        assertThat(converter.fromMessage(message("ITEM_ARCHIVED", "{\"id\":7}"))).isEqualTo(new ItemArchivedEvent(7L));
    }

    @Test
    void readsADeletedItemFromAdminApi() {
        assertThat(converter.fromMessage(message("ITEM_DELETED", "{\"id\":7}"))).isEqualTo(new ItemDeletedEvent(7L));
    }

    private static Message message(String typeId, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", typeId);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
