package org.shpytchuk.notification.service;

import io.notifyhub.core.NotifyHub;
import io.notifyhub.core.dedup.DeduplicationStore;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotifyHub notify;
    private final @Nullable DeduplicationStore deduplication;
    private final List<ChannelRoute> routes;

    public NotificationSender(NotifyHub notify) {
        this(notify, Optional.empty());
    }

    @Autowired
    public NotificationSender(NotifyHub notify, Optional<DeduplicationStore> deduplication) {
        this(notify, deduplication, ChannelRoute.ALL);
    }

    NotificationSender(NotifyHub notify, Optional<DeduplicationStore> deduplication, List<ChannelRoute> routes) {
        this.notify = notify;
        this.deduplication = deduplication.orElse(null);
        this.routes = List.copyOf(routes);
        for (ChannelRoute route : this.routes) {
            if (!isConfigured(route)) {
                log.info("Channel {} is not configured, notifications will skip it", route.name());
            }
        }
    }

    public Delivery send(NotificationRequestedEvent event) {
        Delivery delivery = new Delivery(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (ChannelRoute route : routes) {
            String recipient = route.recipient(event);
            if (recipient != null) {
                sendVia(route, recipient, event, delivery);
            }
        }
        return delivery;
    }

    private void sendVia(ChannelRoute route, String recipient, NotificationRequestedEvent event, Delivery delivery) {
        String name = route.name();
        if (!isConfigured(route)) {
            log.warn("Channel {} is not configured, skipping notification {}", name, event.deduplicationKey());
            delivery.notConfigured().add(name);
            return;
        }

        String key = deduplicationKey(event, route, recipient);
        if (deduplication != null && deduplication.isDuplicate(key)) {
            delivery.duplicates().add(name);
            return;
        }

        log.debug("Sending notification {} via {}", event.deduplicationKey(), name);
        route.address(notify, recipient)
                .via(route.channel())
                .subject(event.subject())
                .content(event.message())
                .deduplicationKey(key)
                .send();
        delivery.sent().add(name);
    }

    private boolean isConfigured(ChannelRoute route) {
        return notify.getRegisteredChannels().contains(route.name());
    }

    private static String deduplicationKey(NotificationRequestedEvent event, ChannelRoute route, String recipient) {
        String key = event.deduplicationKey() == null ? recipient : event.deduplicationKey();
        return "%s:%s".formatted(key, route.channel().name());
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
