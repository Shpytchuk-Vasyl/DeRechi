package org.shpytchuk.notification.service;

import io.notifyhub.core.Channel;
import io.notifyhub.core.NotificationBuilder;
import io.notifyhub.core.NotifyHub;
import io.notifyhub.core.dedup.DeduplicationStore;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotifyHub notify;
    private final DeduplicationStore deduplication;

    public NotificationSender(NotifyHub notify) {
        this(notify, Optional.empty());
    }

    @Autowired
    public NotificationSender(NotifyHub notify, Optional<DeduplicationStore> deduplication) {
        this.notify = notify;
        this.deduplication = deduplication.orElse(null);
        for (Channel channel : List.of(Channel.EMAIL, Channel.SMS)) {
            if (!isConfigured(channel)) {
                log.info("Channel {} is not configured, notifications will skip it", channelName(channel));
            }
        }
    }

    public Delivery send(NotificationRequestedEvent event) {
        Delivery delivery = new Delivery(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        if (event.email() != null) {
            sendVia(Channel.EMAIL, event, delivery);
        }
        if (event.phone() != null) {
            sendVia(Channel.SMS, event, delivery);
        }
        return delivery;
    }

    private void sendVia(Channel target, NotificationRequestedEvent event, Delivery delivery) {
        String name = channelName(target);
        if (!isConfigured(target)) {
            log.warn("Channel {} is not configured, skipping notification {}", name, event.deduplicationKey());
            delivery.notConfigured().add(name);
            return;
        }

        String key = deduplicationKey(event, target);
        if (deduplication != null && deduplication.isDuplicate(key)) {
            delivery.duplicates().add(name);
            return;
        }

        log.debug("Sending notification {} via {}", event.deduplicationKey(), name);
        builder(target, event)
                .via(target)
                .subject(event.subject())
                .content(event.message())
                .deduplicationKey(key)
                .send();
        delivery.sent().add(name);
    }

    private NotificationBuilder builder(Channel target, NotificationRequestedEvent event) {
        String recipient = event.getRecipient(target);
        return target == Channel.EMAIL ? notify.to(recipient) : notify.toPhone(recipient);
    }

    private boolean isConfigured(Channel channel) {
        return notify.getRegisteredChannels().contains(channelName(channel));
    }

    private static String channelName(Channel channel) {
        return channel.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String deduplicationKey(NotificationRequestedEvent event, Channel target) {
        String key = event.deduplicationKey() == null ? event.getRecipient(target) : event.deduplicationKey();
        return "%s:%s".formatted(key, target.name());
    }

    public record Delivery(List<String> sent, List<String> duplicates, List<String> notConfigured) {

        public boolean delivered() {
            return !sent.isEmpty() || !duplicates.isEmpty();
        }

        @Override
        public String toString() {
            StringJoiner parts = new StringJoiner(", ");
            if (!sent.isEmpty()) {
                parts.add("sent via " + String.join(" and ", sent));
            }
            if (!duplicates.isEmpty()) {
                parts.add("already sent via " + String.join(" and ", duplicates));
            }
            if (!notConfigured.isEmpty()) {
                parts.add("skipped " + String.join(" and ", notConfigured) + " (not configured)");
            }
            return parts.length() == 0 ? "no email or phone" : parts.toString();
        }
    }
}
