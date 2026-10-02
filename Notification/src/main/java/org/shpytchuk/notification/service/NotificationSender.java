package org.shpytchuk.notification.service;

import io.notifyhub.core.Channel;
import io.notifyhub.core.NotificationBuilder;
import io.notifyhub.core.NotifyHub;
import io.notifyhub.core.dedup.DuplicateNotificationException;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotifyHub notify;

    public NotificationSender(NotifyHub notify) {
        this.notify = notify;
    }

    public void send(NotificationRequestedEvent event) {
        if (event.email() != null) {
            sendVia(Channel.EMAIL, event);
        }
        if (event.phone() != null) {
            sendVia(Channel.SMS, event);
        }
    }

    private void sendVia(Channel target, NotificationRequestedEvent event) {
        if (!isConfigured(target)) {
            log.warn("Канал {} не налаштований, пропускаємо {}", target, event.subject());
            return;
        }

        log.debug("Надсилаємо {} через {}", event.subject(), target);
        try {
            builder(target, event)
                    .via(target)
                    .subject(event.subject())
                    .content(event.message())
                    .deduplicationKey(deduplicationKey(event, target))
                    .send();
        } catch (DuplicateNotificationException duplicate) {
            log.info("Повтор {} через {}, не надсилаємо вдруге", event.deduplicationKey(), target);
        }
    }

    private NotificationBuilder builder(Channel target, NotificationRequestedEvent event) {
        String recipient = event.getRecipient(target);
        return target == Channel.EMAIL ? notify.to(recipient) : notify.toPhone(recipient);
    }

    private boolean isConfigured(Channel channel) {
        return notify.getRegisteredChannels().contains(channel.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static String deduplicationKey(NotificationRequestedEvent event, Channel target) {
        String key = event.deduplicationKey() == null ? event.getRecipient(target) : event.deduplicationKey();
        return "%s:%s".formatted(key, target.name());
    }
}
