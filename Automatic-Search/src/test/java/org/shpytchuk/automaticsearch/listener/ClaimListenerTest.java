package org.shpytchuk.automaticsearch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.automaticsearch.event.ClaimEvent;
import org.shpytchuk.automaticsearch.service.ClaimHandler;
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

class ClaimListenerTest {

    private ClaimHandler claimed;
    private ClaimHandler returned;
    private ClaimListener listener;

    @BeforeEach
    void setUp() {
        claimed = handler("item.lost.claimed");
        returned = handler("item.found.returned");
        listener = new ClaimListener(List.of(claimed, returned));
    }

    @Test
    void dispatchesByTheReceivedRoutingKey() {
        ClaimEvent event = event(7L);

        listener.onClaim(event, message("item.found.returned"));

        verify(returned).handle(event);
        verify(claimed, never()).handle(any());
    }

    @Test
    void rejectsAKeyNobodyHandlesWithoutRequeue() {
        assertThatThrownBy(() -> listener.onClaim(event(7L), message("item.lost.archived")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("item.lost.archived");

        verify(claimed, never()).handle(any());
        verify(returned, never()).handle(any());
    }

    private static ClaimHandler handler(String routingKey) {
        ClaimHandler handler = mock(ClaimHandler.class);
        when(handler.routingKey()).thenReturn(routingKey);
        return handler;
    }

    private static ClaimEvent event(Long id) {
        ClaimEvent event = new ClaimEvent();
        event.setId(id);
        return event;
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
