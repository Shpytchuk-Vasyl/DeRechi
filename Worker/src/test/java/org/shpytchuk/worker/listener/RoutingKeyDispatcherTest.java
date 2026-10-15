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
            new RoutingKeyDispatcher<>(List.of("notice.lost.created", "notice.found.created"), Function.identity());

    @Test
    void resolvesTheHandlerOfTheReceivedRoutingKey() {
        assertThat(dispatcher.resolve(message("notice.found.created"))).isEqualTo("notice.found.created");
        assertThat(dispatcher.routingKeys()).containsExactlyInAnyOrder("notice.lost.created", "notice.found.created");
    }

    @Test
    void rejectsAKeyNobodyHandlesWithoutRequeue() {
        assertThatThrownBy(() -> dispatcher.resolve(message("claim.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("No handler for the key claim.lost.claimed");
    }

    @Test
    void refusesTwoHandlersForOneKey() {
        assertThatThrownBy(() -> new RoutingKeyDispatcher<>(List.of("first", "second"), handler -> "notice.lost.created"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice.lost.created")
                .hasMessageContaining("first")
                .hasMessageContaining("second");
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
