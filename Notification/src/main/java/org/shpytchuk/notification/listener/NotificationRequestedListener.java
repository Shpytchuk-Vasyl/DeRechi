package org.shpytchuk.notification.listener;

import io.notifyhub.core.channel.NotificationSendException;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.service.NotificationSender;
import org.shpytchuk.notification.service.NotificationSender.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
public class NotificationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRequestedListener.class);

    private final NotificationSender sender;

    public NotificationRequestedListener(NotificationSender sender) {
        this.sender = sender;
    }

    @RabbitListener(queues = "${derechi.notification.queue}")
    public void onNotificationRequested(NotificationRequestedEvent event, Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String key = event.deduplicationKey();
        long start = System.nanoTime();

        Delivery delivery;
        try {
            delivery = sender.send(event);
        } catch (RuntimeException e) {
            String cause = redact(getMostSpecificCause(e).toString(), event);
            log.error("Notification {} {} failed{} in {} ms: {}", routingKey, key, channel(e), millisSince(start), cause);
            throw new AmqpRejectAndDontRequeueException("Notification %s %s failed".formatted(routingKey, key),
                    new RedactedCause(cause));
        }

        if (delivery.delivered()) {
            log.info("Notification {} {} {} in {} ms", routingKey, key, delivery, millisSince(start));
        } else {
            log.warn("Notification {} {} sent nothing: {}", routingKey, key, delivery);
        }
    }

    private static String channel(RuntimeException e) {
        return e instanceof NotificationSendException failure ? " on " + failure.getChannelName() : "";
    }

    static String redact(String text, NotificationRequestedEvent event) {
        String result = text;
        if (event.email() != null && !event.email().isBlank()) {
            result = result.replace(event.email(), "<email>");
        }
        if (event.phone() != null && !event.phone().isBlank()) {
            result = result.replace(event.phone(), "<phone>");
            String digits = event.phone().replaceAll("\\D", "");
            if (digits.length() >= 7) {
                result = result.replace(digits, "<phone>");
            }
        }
        return result;
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    static final class RedactedCause extends RuntimeException {

        RedactedCause(String message) {
            super(message, null, false, false);
        }
    }
}
