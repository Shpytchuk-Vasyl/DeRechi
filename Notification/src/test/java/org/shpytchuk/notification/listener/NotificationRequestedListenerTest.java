package org.shpytchuk.notification.listener;

import io.notifyhub.core.channel.NotificationSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;
import org.shpytchuk.notification.service.NotificationSender;
import org.shpytchuk.notification.service.NotificationSender.Delivery;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(sender.send(EVENT)).thenReturn(new Delivery(List.of("email", "sms"), List.of(), List.of()));

        listener.onNotificationRequested(EVENT, message("notification.claim.created"));

        verify(sender).send(EVENT);
    }

    @Test
    void rejectsAFailedDeliveryWithoutRequeueSoItGoesStraightToTheDeadLetterQueue() {
        doThrow(new IllegalStateException("SMTP is down")).when(sender).send(EVENT);

        assertThatThrownBy(() -> listener.onNotificationRequested(EVENT, message("notification.match.found")))
                .as("NotifyHub has already retried; Spring AMQP's retry would only multiply the attempts")
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasRootCauseMessage("java.lang.IllegalStateException: SMTP is down");
    }

    @Test
    void rethrowsTheFailureWithoutTheRecipientsInItsCauseChain() {
        doThrow(new NotificationSendException("email", "Failed to send email to 'user@derechi.local'",
                new IllegalStateException("550 <user@derechi.local> rejected, SMS to 380501234567 or +380501234567")))
                .when(sender).send(EVENT);

        assertThatThrownBy(() -> listener.onNotificationRequested(EVENT, message("notification.match.found")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .satisfies(rejected -> {
                    assertThat(rejected.getCause()).isInstanceOf(NotificationRequestedListener.RedactedCause.class)
                            .hasNoCause()
                            .hasMessage("java.lang.IllegalStateException: 550 <<email>> rejected, SMS to <phone> or <phone>");
                    assertThat(rejected).hasStackTraceContaining("<email>")
                            .satisfies(e -> assertThat(stackTrace(e))
                                    .doesNotContain("user@derechi.local", "380501234567"));
                });
    }

    private static String stackTrace(Throwable e) {
        java.io.StringWriter out = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(out));
        return out.toString();
    }

    @Test
    void keepsTheRecipientOutOfTheLoggedCause() {
        assertThat(NotificationRequestedListener.redact(
                "SMTPAddressFailedException: 550 <user@derechi.local> unknown; SMS to 380501234567 or +380501234567",
                EVENT))
                .isEqualTo("SMTPAddressFailedException: 550 <<email>> unknown; SMS to <phone> or <phone>");
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
