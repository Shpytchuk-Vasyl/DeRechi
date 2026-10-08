package org.shpytchuk.worker.listener;

import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.handler.ItemCreatedHandler;
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

    private final Map<String, ItemCreatedHandler> handlers;

    public ItemCreatedListener(List<ItemCreatedHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ItemCreatedHandler::routingKey, Function.identity()));
    }

    @RabbitListener(queues = "${derechi.items.queue}")
    public void onItemCreated(ItemCreatedEvent event, Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        ItemCreatedHandler handler = handlers.get(routingKey);
        if (handler == null) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey);
        }
        handler.onItemCreated(event);
    }
}
