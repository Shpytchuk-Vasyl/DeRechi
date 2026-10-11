package org.shpytchuk.worker.listener;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingKeyDispatcherTest {

    private final RoutingKeyDispatcher<String> dispatcher =
            new RoutingKeyDispatcher<>(List.of("item.lost.created", "item.found.created"), Function.identity());

    @Test
    void resolvesTheHandlerOfTheReceivedRoutingKey() {
        assertThat(dispatcher.resolve(message("item.found.created"))).isEqualTo("item.found.created");
        assertThat(dispatcher.routingKeys()).containsExactlyInAnyOrder("item.lost.created", "item.found.created");
    }

    @Test
    void rejectsAKeyNobodyHandlesWithoutRequeue() {
        assertThatThrownBy(() -> dispatcher.resolve(message("item.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("No handler for the key item.lost.claimed");
    }

    @Test
    void refusesTwoHandlersForOneKey() {
        assertThatThrownBy(() -> new RoutingKeyDispatcher<>(List.of("first", "second"), handler -> "item.lost.created"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("item.lost.created")
                .hasMessageContaining("first")
                .hasMessageContaining("second");
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
