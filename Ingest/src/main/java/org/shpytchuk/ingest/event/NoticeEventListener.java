package org.shpytchuk.ingest.event;

import lombok.AllArgsConstructor;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.publish.telegram.ChannelMirror;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
@RabbitListener(queues = "${derechi.events.queue}")
public class NoticeEventListener {

    private static final Pattern NOTICE_KEY = Pattern.compile("notice\\.(lost|found)\\.([a-z]+)");
    private static final String CREATED = "created";
    private static final String ARCHIVED = "archived";
    private static final String DELETED = "deleted";

    private final ChannelMirror mirror;

    @RabbitHandler
    public void onCreated(ItemCreatedEvent event, Message message) {
        mirror.created(kind(message, CREATED), event);
    }

    @RabbitHandler
    public void onArchived(ItemArchivedEvent event, Message message) {
        mirror.removed(kind(message, ARCHIVED), event.id(), ARCHIVED);
    }

    @RabbitHandler
    public void onDeleted(ItemDeletedEvent event, Message message) {
        mirror.removed(kind(message, DELETED), event.id(), DELETED);
    }

    @RabbitHandler(isDefault = true)
    public void onUnknown(Object payload, Message message) {
        throw new AmqpRejectAndDontRequeueException("No handler for %s with the key %s".formatted(
                payload.getClass().getName(), routingKey(message)));
    }

    private static ItemKind kind(Message message, String verb) {
        Matcher key = NOTICE_KEY.matcher(String.valueOf(routingKey(message)));
        if (!key.matches() || !key.group(2).equals(verb)) {
            throw new AmqpRejectAndDontRequeueException("No handler for the key " + routingKey(message));
        }
        return ItemKind.valueOf(key.group(1).toUpperCase(Locale.ROOT));
    }

    private static String routingKey(Message message) {
        return message.getMessageProperties().getReceivedRoutingKey();
    }
}
