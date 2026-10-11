package org.shpytchuk.notification.service;

import io.notifyhub.core.Channel;
import io.notifyhub.core.NotificationBuilder;
import io.notifyhub.core.NotifyHub;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.notification.event.NotificationRequestedEvent;

import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

record ChannelRoute(Channel channel,
                    Function<NotificationRequestedEvent, @Nullable String> contact,
                    BiFunction<NotifyHub, String, NotificationBuilder> addressing) {

    static final ChannelRoute EMAIL = new ChannelRoute(Channel.EMAIL, NotificationRequestedEvent::email, NotifyHub::to);

    static final ChannelRoute SMS = new ChannelRoute(Channel.SMS, NotificationRequestedEvent::phone, NotifyHub::toPhone);

    static final List<ChannelRoute> ALL = List.of(EMAIL, SMS);

    String name() {
        return channel.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Nullable String recipient(NotificationRequestedEvent event) {
        return contact.apply(event);
    }

    NotificationBuilder address(NotifyHub notify, String recipient) {
        return addressing.apply(notify, recipient);
    }
}
