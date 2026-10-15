package org.shpytchuk.worker.listener;

import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.event.ArchiveRequestedEvent;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.handler.ClaimHandler;
import org.shpytchuk.worker.handler.ItemCreatedHandler;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RabbitListener(queues = "${derechi.worker.queue}")
public class WorkerQueueListener {

    static final String ARCHIVE = "archive";

    private final RoutingKeyDispatcher<ItemCreatedHandler> created;
    private final RoutingKeyDispatcher<ClaimHandler> claims;
    private final RoutingKeyDispatcher<ItemKind> archives =
            new RoutingKeyDispatcher<>(List.of(ItemKind.values()), kind -> kind.noticeKey(ARCHIVE));
    private final ItemArchiver archiver;

    public WorkerQueueListener(List<ItemCreatedHandler> created, List<ClaimHandler> claims, ItemArchiver archiver) {
        this.created = new RoutingKeyDispatcher<>(created, ItemCreatedHandler::routingKey);
        this.claims = new RoutingKeyDispatcher<>(claims, ClaimHandler::routingKey);
        this.archiver = archiver;
    }

    @RabbitHandler
    public void onItemCreated(ItemCreatedEvent event, Message message) {
        created.resolve(message).onItemCreated(event);
    }

    @RabbitHandler
    public void onClaim(ClaimEvent event, Message message) {
        claims.resolve(message).handle(event);
    }

    @RabbitHandler
    public void onArchiveRequested(ArchiveRequestedEvent event, Message message) {
        ItemKind kind = archives.resolve(message);
        log.info("{} asked to archive {} item {}", event.getActor(), kind, event.getId());
        archiver.archive(kind, event.getId());
    }

    @RabbitHandler(isDefault = true)
    public void onUnknown(Object payload, Message message) {
        throw new AmqpRejectAndDontRequeueException("No handler for %s with the key %s".formatted(
                payload.getClass().getName(), message.getMessageProperties().getReceivedRoutingKey()));
    }
}
