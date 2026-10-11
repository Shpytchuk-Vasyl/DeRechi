package org.shpytchuk.worker.listener;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class RoutingKeyDispatcher<H> {

    private final Map<String, H> handlers;

    public RoutingKeyDispatcher(Collection<? extends H> handlers, Function<? super H, String> routingKey) {
        Map<String, H> byKey = new HashMap<>();
        for (H handler : handlers) {
            String key = routingKey.apply(handler);
            H previous = byKey.put(key, handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two handlers for the routing key " + key + ": " + previous + " and " + handler);
            }
        }
        this.handlers = Map.copyOf(byKey);
    }

    public H resolve(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        H handler = handlers.get(routingKey);
        if (handler == null) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey);
        }
        return handler;
    }

    public Set<String> routingKeys() {
        return handlers.keySet();
    }
}
