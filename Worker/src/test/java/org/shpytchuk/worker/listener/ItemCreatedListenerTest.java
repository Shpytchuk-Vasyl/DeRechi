package org.shpytchuk.worker.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.handler.ItemCreatedHandler;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemCreatedListenerTest {

    private ItemCreatedHandler lostCreated;
    private ItemCreatedHandler foundCreated;
    private ItemCreatedListener listener;

    @BeforeEach
    void setUp() {
        lostCreated = handler("item.lost.created");
        foundCreated = handler("item.found.created");
        listener = new ItemCreatedListener(List.of(lostCreated, foundCreated));
    }

    @Test
    void dispatchesByTheReceivedRoutingKey() {
        ItemCreatedEvent event = new ItemCreatedEvent();

        listener.onItemCreated(event, message("item.found.created"));

        verify(foundCreated).onItemCreated(event);
        verify(lostCreated, never()).onItemCreated(any());
    }

    @Test
    void rejectsAKeyNobodyHandlesWithoutRequeue() {
        assertThatThrownBy(() -> listener.onItemCreated(new ItemCreatedEvent(), message("item.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("item.lost.claimed");

        verify(lostCreated, never()).onItemCreated(any());
        verify(foundCreated, never()).onItemCreated(any());
    }

    @Test
    void refusesToStartWithTwoHandlersForOneKey() {
        assertThatThrownBy(() -> new ItemCreatedListener(List.of(lostCreated, handler("item.lost.created"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("item.lost.created");
    }

    private static ItemCreatedHandler handler(String routingKey) {
        ItemCreatedHandler handler = mock(ItemCreatedHandler.class);
        when(handler.routingKey()).thenReturn(routingKey);
        return handler;
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
