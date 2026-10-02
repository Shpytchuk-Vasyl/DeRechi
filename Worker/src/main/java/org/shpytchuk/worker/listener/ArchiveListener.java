package org.shpytchuk.worker.listener;

import org.shpytchuk.worker.event.ArchiveRequestedEvent;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ArchiveListener {

    private static final Logger log = LoggerFactory.getLogger(ArchiveListener.class);

    static final String VERB = "archive";

    private static final Map<String, ItemKind> KINDS = Arrays.stream(ItemKind.values())
            .collect(Collectors.toMap(kind -> kind.routingKey(VERB), Function.identity()));

    private final ItemArchiver archiver;

    public ArchiveListener(ItemArchiver archiver) {
        this.archiver = archiver;
    }

    @RabbitListener(queues = "${derechi.archive.queue}")
    public void onArchiveRequested(ArchiveRequestedEvent event, Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        ItemKind kind = KINDS.get(routingKey);
        if (kind == null) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey);
        }

        log.info("{} asked to archive {} item {}", event.getActor(), kind, event.getId());
        archiver.archive(kind, event.getId());
    }
}
