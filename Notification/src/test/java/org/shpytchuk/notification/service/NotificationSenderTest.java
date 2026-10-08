package org.shpytchuk.notification.service;

import io.notifyhub.core.Channel;
import io.notifyhub.core.NotifyHub;
import io.notifyhub.core.dedup.InMemoryDeduplicationStore;
import io.notifyhub.core.testing.SentNotification;
import io.notifyhub.core.testing.TestNotifyHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;

import java.util.Optional;

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
        sender.send(event("+380501234567", "user@derechi.local", "match:1:2"));

        assertThat(hub.sent())
                .extracting(SentNotification::channel, SentNotification::recipient, SentNotification::subject)
                .containsExactly(
                        tuple("email", "user@derechi.local", "Знахідка"),
                        tuple("sms", "+380501234567", "Знахідка"));
    }

    @Test
    void passesMessageAsContent() {
        sender.send(event("+380501234567", "user@derechi.local", "match:1:2"));

        assertThat(hub.sent("email")).singleElement()
                .extracting(SentNotification::content)
                .isEqualTo("Схоже, ми знайшли вашу річ");
    }

    @Test
    void skipsContactsTheEventDoesNotCarry() {
        sender.send(event(null, "user@derechi.local", "match:1:2"));

        assertThat(hub.sent()).extracting(SentNotification::channel).containsExactly("email");
    }

    @Test
    void skipsChannelsThatAreNotConfigured() {
        NotifyHub emailOnly = NotifyHub.builder().channel(hub.hub().getChannel("email").orElseThrow()).build();
        NotificationSender sender = new NotificationSender(emailOnly);

        NotificationSender.Delivery delivery = sender.send(event("+380501234567", "user@derechi.local", "match:1:2"));

        assertThat(hub.sent()).extracting(SentNotification::channel).containsExactly("email");
        assertThat(delivery).hasToString("sent via email, skipped sms (not configured)");
    }

    @Test
    void reportsAnEventWithoutContacts() {
        NotificationSender.Delivery delivery = sender.send(event(null, null, "match:1:2"));

        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery).hasToString("no email or phone");
    }

    @Test
    void sendsTwoDifferentMessagesToTheSameRecipient() {
        sender.send(event("+380501234567", "user@derechi.local", "claim:lost:1"));
        sender.send(event("+380501234567", "user@derechi.local", "claim:lost:2"));

        assertThat(hub.sent("email")).hasSize(2);
    }

    @Test
    void dropsAReplayOfTheSameMessage() {
        InMemoryDeduplicationStore store = new InMemoryDeduplicationStore();
        NotifyHub deduplicating = NotifyHub.builder()
                .channel(hub.hub().getChannel("email").orElseThrow())
                .deduplicationStore(store)
                .build();
        NotificationSender sender = new NotificationSender(deduplicating, Optional.of(store));

        sender.send(event(null, "user@derechi.local", "claim:lost:1"));
        NotificationSender.Delivery replay = sender.send(event(null, "user@derechi.local", "claim:lost:1"));

        assertThat(hub.sent("email")).hasSize(1);
        assertThat(replay).hasToString("already sent via email");
    }

    private static NotificationRequestedEvent event(String phone, String email, String deduplicationKey) {
        return new NotificationRequestedEvent(
                "Знахідка",
                "Схоже, ми знайшли вашу річ",
                phone,
                email,
                new SocialMediaEnum[0],
                deduplicationKey);
    }
}
