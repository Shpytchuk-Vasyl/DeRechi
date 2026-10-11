package org.shpytchuk.notification.service;

import io.notifyhub.core.Channel;
import io.notifyhub.core.NotifyHub;
import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRouteTest {

    private static final NotificationRequestedEvent EVENT = new NotificationRequestedEvent(
            "Знахідка", "Схоже, ми знайшли вашу річ", "+380501234567", "user@derechi.local",
            new SocialMediaEnum[0], "match:1:2");

    @Test
    void emailGoesToTheEmailAndSmsToThePhone() {
        assertThat(ChannelRoute.EMAIL.recipient(EVENT)).isEqualTo("user@derechi.local");
        assertThat(ChannelRoute.SMS.recipient(EVENT)).isEqualTo("+380501234567");
    }

    @Test
    void aMissingContactLeavesTheRouteWithoutARecipient() {
        NotificationRequestedEvent emailOnly = new NotificationRequestedEvent(
                "Знахідка", "Схоже, ми знайшли вашу річ", null, "user@derechi.local", new SocialMediaEnum[0], "match:1:2");

        assertThat(ChannelRoute.SMS.recipient(emailOnly)).isNull();
        assertThat(ChannelRoute.EMAIL.recipient(emailOnly)).isEqualTo("user@derechi.local");
    }

    @Test
    void namesTheChannelTheWayNotifyHubRegistersIt() {
        ChannelRoute googleChat = new ChannelRoute(Channel.GOOGLE_CHAT, NotificationRequestedEvent::email, NotifyHub::to);

        assertThat(ChannelRoute.EMAIL.name()).isEqualTo("email");
        assertThat(ChannelRoute.SMS.name()).isEqualTo("sms");
        assertThat(googleChat.name()).isEqualTo("google-chat");
    }

    @Test
    void emailIsTriedBeforeSms() {
        assertThat(ChannelRoute.ALL).containsExactly(ChannelRoute.EMAIL, ChannelRoute.SMS);
    }
}
