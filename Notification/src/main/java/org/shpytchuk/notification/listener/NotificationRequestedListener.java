package org.shpytchuk.notification.listener;

import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
        log.debug("Отримали {} з ключем {}", event, routingKey);

        try {
            sender.send(event);
        } catch (Exception e) {
            String erroMsg = "Can't notify user :" + event.email();
            log.error(erroMsg);
            throw new AmqpRejectAndDontRequeueException(erroMsg);
        }
    }
}
