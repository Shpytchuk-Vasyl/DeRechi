package org.shpytchuk.automaticsearch.listener;

import org.shpytchuk.automaticsearch.event.ClaimEvent;
import org.shpytchuk.automaticsearch.service.ClaimHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ClaimListener.class);

    private final Map<String, ClaimHandler> handlers;

    public ClaimListener(List<ClaimHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ClaimHandler::routingKey, Function.identity()));
    }

    @RabbitListener(queues = "${derechi.claims.queue}")
    public void onClaim(ClaimEvent event, Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        log.debug("Got claim {} with {} key", event.getId(), routingKey);

        ClaimHandler handler = handlers.get(routingKey);
        if (handler == null) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey);
        }
        handler.handle(event);
    }
}
