package org.shpytchuk.automaticsearch.listener;

import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;
import org.shpytchuk.automaticsearch.service.ItemCreatedHandler;
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
public class ItemCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(ItemCreatedListener.class);


    private final Map<String, ItemCreatedHandler> handlers;

    public ItemCreatedListener(List<ItemCreatedHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ItemCreatedHandler::routingKey, Function.identity()));
    }

    @RabbitListener(queues = "${derechi.items.queue}")
    public void onItemCreated(ItemCreatedEvent event, Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        log.debug("Get {} with {} key", event, routingKey);

        ItemCreatedHandler handler = handlers.get(routingKey);
        if (handler == null) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey);
        }
        handler.onItemCreated(event);
    }
}
