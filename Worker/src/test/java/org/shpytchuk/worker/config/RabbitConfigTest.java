package org.shpytchuk.worker.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.worker.event.ArchiveRequestedEvent;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.event.NotificationRequestedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The modules share no classes, only {@code @EventType} ids in the {@code __TypeId__} header. These tests pin
 * the ids this module reads and writes; the other side of each pair is pinned in the producer's own module.
 */
class RabbitConfigTest {

    private final MessageConverter converter = new RabbitConfig().jsonMessageConverter(JsonMapper.builder().build());

    @Test
    void readsAnItemCreatedEventFromClientApi() {
        Object event = converter.fromMessage(message("ITEM_CREATED",
                "{\"id\":7,\"date\":\"2026-09-10\",\"category\":2,\"lat\":49.84,\"lon\":24.03,\"title\":\"Wallet\"}"));

        assertThat(event).isInstanceOfSatisfying(ItemCreatedEvent.class, created -> {
            assertThat(created.getId()).isEqualTo(7L);
            assertThat(created.getDate()).isEqualTo(LocalDate.of(2026, 9, 10));
            assertThat(created.getCategory()).isEqualTo(2L);
            assertThat(created.getTitle()).isEqualTo("Wallet");
        });
    }

    @Test
    void readsAClaimEventFromClientApi() {
        assertThat(converter.fromMessage(message("CLAIM", "{\"id\":42}")))
                .isInstanceOfSatisfying(ClaimEvent.class, claim -> assertThat(claim.getId()).isEqualTo(42L));
    }

    @Test
    void readsAnArchiveRequestFromAdminApi() {
        assertThat(converter.fromMessage(message("ARCHIVE_REQUESTED", "{\"id\":5,\"actor\":\"admin@derechi.local\"}")))
                .isInstanceOfSatisfying(ArchiveRequestedEvent.class, request -> {
                    assertThat(request.getId()).isEqualTo(5L);
                    assertThat(request.getActor()).isEqualTo("admin@derechi.local");
                });
    }

    @Test
    void labelsTheNotificationItSendsWithTheTypeIdNotTheClassName() {
        NotificationRequestedEvent event = new NotificationRequestedEvent("Subject", "Body", "+380671234567",
                "owner@example.com", new SocialMediaEnum[]{SocialMediaEnum.VIBER}, "claim:lost:42");

        Message message = converter.toMessage(event, new MessageProperties());

        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", "NOTIFICATION");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"deduplicationKey\":\"claim:lost:42\"")
                .contains("\"socialMedias\":[\"VIBER\"]");
    }

    private static Message message(String typeId, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", typeId);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
