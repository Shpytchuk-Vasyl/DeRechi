package org.shpytchuk.clientapi.event;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.config.ClaimsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@AllArgsConstructor
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ClaimsProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ClaimChange change) {
        ClaimEvent event = new ClaimEvent(change.claimId());
        rabbitTemplate.convertAndSend(properties.exchange(), change.routingKey(), event);
        log.debug("Published {} with key {}", event, change.routingKey());
    }
}
