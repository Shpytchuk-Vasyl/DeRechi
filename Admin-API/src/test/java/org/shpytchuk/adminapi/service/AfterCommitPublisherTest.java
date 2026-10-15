package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.event.ItemDeletedEvent;
import org.shpytchuk.adminapi.service.AfterCommitPublisher.Outgoing;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AfterCommitPublisherTest {

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AfterCommitPublisher publisher = new AfterCommitPublisher(events, rabbitTemplate);

    @Test
    void handsTheMessageToTheTransactionInsteadOfSendingIt() {
        publisher.publish("notice.status.exchange", "notice.lost.deleted", new ItemDeletedEvent(7L));

        verify(events).publishEvent(new Outgoing("notice.status.exchange", "notice.lost.deleted", new ItemDeletedEvent(7L)));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void sendsOnceTheTransactionCommits() {
        publisher.send(new Outgoing("notice.status.exchange", "notice.lost.deleted", new ItemDeletedEvent(7L)));

        verify(rabbitTemplate).convertAndSend("notice.status.exchange", "notice.lost.deleted", new ItemDeletedEvent(7L));
    }

    @Test
    void aBrokerFailureAfterTheCommitDoesNotReachTheCaller() {
        doThrow(new AmqpConnectException(new ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatCode(() -> publisher.send(new Outgoing("notice.status.exchange", "notice.lost.deleted",
                new ItemDeletedEvent(7L)))).doesNotThrowAnyException();
    }
}
