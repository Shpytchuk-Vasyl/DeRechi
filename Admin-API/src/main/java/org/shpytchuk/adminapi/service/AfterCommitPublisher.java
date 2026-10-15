package org.shpytchuk.adminapi.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@AllArgsConstructor
@Slf4j
public class AfterCommitPublisher {

    private final ApplicationEventPublisher events;
    private final RabbitTemplate rabbitTemplate;

    public void publish(String exchange, String routingKey, Object payload) {
        events.publishEvent(new Outgoing(exchange, routingKey, payload));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void send(Outgoing message) {
        try {
            rabbitTemplate.convertAndSend(message.exchange(), message.routingKey(), message.payload());
        } catch (AmqpException e) {
            log.warn("{} with key {} was not sent: {}", message.payload(), message.routingKey(),
                    getMostSpecificCause(e).toString());
        }
    }

    public record Outgoing(String exchange, String routingKey, Object payload) {
    }
}
