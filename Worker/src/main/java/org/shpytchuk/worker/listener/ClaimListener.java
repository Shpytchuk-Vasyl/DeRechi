package org.shpytchuk.worker.listener;

import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.handler.ClaimHandler;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ClaimListener {

    private final Map<String, ClaimHandler> handlers;

    public ClaimListener(List<ClaimHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ClaimHandler::routingKey, Function.identity()));
    }

    @RabbitListener(queues = "${derechi.claims.queue}")
    public void onClaim(ClaimEvent event, Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        ClaimHandler handler = handlers.get(routingKey);
        if (handler == null) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey);
        }
        handler.handle(event);
    }
}
