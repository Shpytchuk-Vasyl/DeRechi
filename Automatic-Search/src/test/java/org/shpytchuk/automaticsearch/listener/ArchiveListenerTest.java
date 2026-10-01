package org.shpytchuk.automaticsearch.listener;

import org.junit.jupiter.api.Test;
import org.shpytchuk.automaticsearch.event.ArchiveRequestedEvent;
import org.shpytchuk.automaticsearch.service.ItemKind;
import org.shpytchuk.automaticsearch.service.ItemArchiver;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArchiveListenerTest {

    private final ItemArchiver archiver = mock(ItemArchiver.class);
    private final ArchiveListener listener = new ArchiveListener(archiver);

    @Test
    void archivesTheKindNamedByTheRoutingKey() {
        listener.onArchiveRequested(event(7L), message("item.found.archive"));

        verify(archiver).archive(ItemKind.FOUND, 7L);
    }

    @Test
    void rejectsAKeyNobodyHandlesWithoutRequeue() {
        assertThatThrownBy(() -> listener.onArchiveRequested(event(7L), message("item.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("item.lost.claimed");

        verify(archiver, never()).archive(any(), anyLong());
    }

    private static ArchiveRequestedEvent event(Long id) {
        ArchiveRequestedEvent event = new ArchiveRequestedEvent();
        event.setId(id);
        event.setActor("admin@derechi.local");
        return event;
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
