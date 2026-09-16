package org.shpytchuk.notification.service;

import io.notifyhub.core.testing.SentNotification;
import io.notifyhub.core.testing.TestNotifyHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class NotificationSenderTest {

    private TestNotifyHub hub;
    private NotificationSender sender;

    @BeforeEach
    void setUp() {
        hub = TestNotifyHub.create();
        sender = new NotificationSender(hub.hub());
    }

    @Test
    void sendsToEmailAndPhone() {
        sender.send(event(new SocialMediaEnum[0]));

        assertThat(hub.sent())
                .extracting(SentNotification::channel, SentNotification::recipient, SentNotification::subject)
                .containsExactly(
                        tuple("email", "user@derechi.local", "Знахідка"),
                        tuple("sms", "+380501234567", "Знахідка"));
    }

    @Test
    void passesMessageAsContent() {
        sender.send(event(new SocialMediaEnum[0]));

        assertThat(hub.sent("email")).singleElement()
                .extracting(SentNotification::content)
                .isEqualTo("Схоже, ми знайшли вашу річ");
    }

    private static NotificationRequestedEvent event(SocialMediaEnum[] socialMedias) {
        return new NotificationRequestedEvent(
                "Знахідка",
                "Схоже, ми знайшли вашу річ",
                "+380501234567",
                "user@derechi.local",
                socialMedias,
                null);
    }
}
