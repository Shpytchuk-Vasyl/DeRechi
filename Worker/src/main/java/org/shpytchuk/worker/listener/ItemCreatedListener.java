package org.shpytchuk.worker.listener;

import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.handler.ItemCreatedHandler;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemCreatedListener {

    private final RoutingKeyDispatcher<ItemCreatedHandler> handlers;

    public ItemCreatedListener(List<ItemCreatedHandler> handlers) {
        this.handlers = new RoutingKeyDispatcher<>(handlers, ItemCreatedHandler::routingKey);
    }

    @RabbitListener(queues = "${derechi.items.queue}")
    public void onItemCreated(ItemCreatedEvent event, Message message) {
        handlers.resolve(message).onItemCreated(event);
    }
}
