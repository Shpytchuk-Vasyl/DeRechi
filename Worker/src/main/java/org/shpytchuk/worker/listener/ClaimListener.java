package org.shpytchuk.worker.listener;

import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.handler.ClaimHandler;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaimListener {

    private final RoutingKeyDispatcher<ClaimHandler> handlers;

    public ClaimListener(List<ClaimHandler> handlers) {
        this.handlers = new RoutingKeyDispatcher<>(handlers, ClaimHandler::routingKey);
    }

    @RabbitListener(queues = "${derechi.claims.queue}")
    public void onClaim(ClaimEvent event, Message message) {
        handlers.resolve(message).handle(event);
    }
}
