package org.shpytchuk.ingest.event;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.publish.telegram.ChannelMirror;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeEventListenerTest {

    private static final ItemCreatedEvent CREATED = new ItemCreatedEvent(7L, LocalDate.of(2026, 10, 9), "KEYS",
            "Площа Ринок", "Ключі", null, null, "UAH", null, "site");

    private final ChannelMirror mirror = mock(ChannelMirror.class);
    private final NoticeEventListener listener = new NoticeEventListener(mirror);

    @Test
    void postsACreatedNoticeOfTheKindInTheKey() {
        listener.onCreated(CREATED, message("notice.found.created"));

        verify(mirror).created(ItemKind.FOUND, CREATED);
    }

    @Test
    void removesAnArchivedOrDeletedNotice() {
        listener.onArchived(new ItemArchivedEvent(7L), message("notice.lost.archived"));
        listener.onDeleted(new ItemDeletedEvent(8L), message("notice.found.deleted"));

        verify(mirror).removed(ItemKind.LOST, 7L, "archived");
        verify(mirror).removed(ItemKind.FOUND, 8L, "deleted");
    }

    @Test
    void rejectsAKeyItDoesNotHandleWithoutRequeue() {
        assertThatThrownBy(() -> listener.onArchived(new ItemArchivedEvent(7L), message("notice.lost.deleted")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("notice.lost.deleted");
        assertThatThrownBy(() -> listener.onCreated(CREATED, message("claim.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("claim.lost.claimed");

        verifyNoInteractions(mirror);
    }

    @Test
    void rejectsAPayloadWithoutAHandlerWithoutRequeue() {
        assertThatThrownBy(() -> listener.onUnknown("payload", message("notice.lost.created")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining(String.class.getName());
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
