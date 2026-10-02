package org.shpytchuk.notification.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;
import org.shpytchuk.notification.service.NotificationSender;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationRequestedListenerTest {

    private static final NotificationRequestedEvent EVENT = new NotificationRequestedEvent(
            "Знахідка", "Схоже, ми знайшли вашу річ", "+380501234567", "user@derechi.local",
            new SocialMediaEnum[0], "match:1:2");

    private NotificationSender sender;
    private NotificationRequestedListener listener;

    @BeforeEach
    void setUp() {
        sender = mock(NotificationSender.class);
        listener = new NotificationRequestedListener(sender);
    }

    @Test
    void handsTheEventToTheSender() {
        listener.onNotificationRequested(EVENT, message("notification.claim.created"));

        verify(sender).send(EVENT);
    }

    @Test
    void rejectsAFailedDeliveryWithoutRequeueSoItGoesStraightToTheDeadLetterQueue() {
        doThrow(new IllegalStateException("SMTP is down")).when(sender).send(EVENT);

        assertThatThrownBy(() -> listener.onNotificationRequested(EVENT, message("notification.match.found")))
                .as("NotifyHub has already retried; Spring AMQP's retry would only multiply the attempts")
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
