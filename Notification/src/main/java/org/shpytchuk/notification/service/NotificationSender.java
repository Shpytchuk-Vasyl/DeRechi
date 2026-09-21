package org.shpytchuk.notification.service;

import io.notifyhub.core.Channel;
import io.notifyhub.core.NotifyHub;
import io.notifyhub.core.dedup.DuplicateNotificationException;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotifyHub notify;

    public NotificationSender(NotifyHub notify) {
        this.notify = notify;
    }

    public void send(NotificationRequestedEvent event) {
        try {
            if (event.email() != null) {
                sendVia(Channel.EMAIL, event);
            }

            List<Channel> targets = targets(event);

            if (targets.isEmpty() && event.phone() != null) {
                sendVia(Channel.SMS, event);
            }

            for (var target : targets) {
                sendVia(target, event);
            }
        } catch (DuplicateNotificationException e) {
        }
        return;
    }

    private void sendVia(Channel target,
                         NotificationRequestedEvent event) {
        log.debug("Надсилаємо {} через {}", event.subject(), target);

        notify.to(event.getRecipient(target))
                .via(target)
                .subject(event.subject())
                .content(event.message())
                .deduplicationKey(deduplicationKey(event, target))
                .send();
    }

    private List<Channel> targets(NotificationRequestedEvent event) {
        Set<String> registered = notify.getRegisteredChannels();

        return Arrays.stream(event.socialMedias())
                .map(m -> m.toChanel())
                .filter(channel -> {
                    if (!registered.contains(channel.name())) {
                        log.warn("Канал {} не налаштований — пропускаємо", channel);
                        return false;
                    }
                    return true;
                })
                .toList();
    }


    private static String deduplicationKey(NotificationRequestedEvent event, Channel target) {
        return "%s:%s".formatted(event.getRecipient(target), target.name());
    }
}
