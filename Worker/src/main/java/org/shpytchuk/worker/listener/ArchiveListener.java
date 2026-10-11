package org.shpytchuk.worker.listener;

import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.event.ArchiveRequestedEvent;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ArchiveListener {

    static final String VERB = "archive";

    private final RoutingKeyDispatcher<ItemKind> kinds =
            new RoutingKeyDispatcher<>(List.of(ItemKind.values()), kind -> kind.routingKey(VERB));

    private final ItemArchiver archiver;

    public ArchiveListener(ItemArchiver archiver) {
        this.archiver = archiver;
    }

    @RabbitListener(queues = "${derechi.archive.queue}")
    public void onArchiveRequested(ArchiveRequestedEvent event, Message message) {
        ItemKind kind = kinds.resolve(message);
        log.info("{} asked to archive {} item {}", event.getActor(), kind, event.getId());
        archiver.archive(kind, event.getId());
    }
}
