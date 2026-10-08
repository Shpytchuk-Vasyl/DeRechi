package org.shpytchuk.clientapi.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.shpytchuk.clientapi.config.EventsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final BlockingDeque<PendingEvent> pending;

    public EventPublisher(RabbitTemplate rabbitTemplate, EventsProperties properties, MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = properties.batchSize();
        this.pending = new LinkedBlockingDeque<>(properties.bufferCapacity());
        Gauge.builder("derechi.events.pending", pending, BlockingDeque::size)
                .description("Events waiting for RabbitMQ")
                .register(meterRegistry);
    }

    public void publish(String exchange, String routingKey, Object event) {
        PendingEvent next = new PendingEvent(exchange, routingKey, event);
        if (!pending.isEmpty()) {
            log.debug("Buffering {} with key {} behind {} pending events", event, routingKey, pending.size());
            buffer(next);
            return;
        }
        try {
            send(rabbitTemplate, next);
        } catch (AmqpException e) {
            log.warn("RabbitMQ is unavailable, buffering {} with key {}: {}", event, routingKey,
                    getMostSpecificCause(e).toString());
            buffer(next);
        }
    }

    @Scheduled(fixedDelayString = "${derechi.events.flush-interval}")
    public void flush() {
        if (!pending.isEmpty() && connected()) {
            sendBatch(batchSize);
        }
    }

    @PreDestroy
    void drain() {
        while (!pending.isEmpty() && connected() && sendBatch(batchSize)) {
        }
        if (!pending.isEmpty()) {
            log.error("Dropping {} unsent events on shutdown: {}", pending.size(), pending);
        }
    }

    int pendingCount() {
        return pending.size();
    }

    /** @return true, if full batch send successfully */
    private boolean sendBatch(int size) {
        List<PendingEvent> batch = new ArrayList<>(size);
        pending.drainTo(batch, size);
        int[] sent = {0};
        try {
            rabbitTemplate.invoke(operations -> {
                for (PendingEvent event : batch) {
                    send(operations, event);
                    sent[0]++;
                }
                return null;
            });
            log.info("Sent {} buffered events, {} left", batch.size(), pending.size());
            return true;
        } catch (AmqpException e) {
            log.warn("RabbitMQ failed after {} of {} buffered events: {}", sent[0], batch.size(),
                    getMostSpecificCause(e).toString());
            for (int i = batch.size() - 1; i >= sent[0]; i--) {
                if (!pending.offerFirst(batch.get(i))) {
                    log.error("Event buffer is full, dropping {}", batch.get(i));
                }
            }
            return false;
        }
    }

    private boolean connected() {
        try (Connection connection = rabbitTemplate.getConnectionFactory().createConnection()) {
            return connection.isOpen();
        } catch (AmqpException e) {
            return false;
        }
    }

    private void buffer(PendingEvent event) {
        if (!pending.offerLast(event)) {
            log.error("Event buffer is full, dropping {}", event);
        }
    }

    private static void send(RabbitOperations operations, PendingEvent event) {
        operations.convertAndSend(event.exchange(), event.routingKey(), event.payload());
        log.debug("Published {} with key {}", event.payload(), event.routingKey());
    }

    private record PendingEvent(String exchange, String routingKey, Object payload) {
    }
}
