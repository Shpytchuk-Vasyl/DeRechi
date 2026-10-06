package org.shpytchuk.clientapi.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.shpytchuk.clientapi.config.EventsProperties;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventPublisherTest {

    private static final String EXCHANGE = "derechi.items";
    private static final AmqpConnectException DOWN = new AmqpConnectException(new ConnectException("refused"));

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ConnectionFactory connectionFactory;

    @Mock
    private Connection connection;

    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        given(rabbitTemplate.getConnectionFactory()).willReturn(connectionFactory);
        given(rabbitTemplate.invoke(any())).willAnswer(invocation ->
                invocation.<OperationsCallback<?>>getArgument(0).doInRabbit(rabbitTemplate));
        publisher = new EventPublisher(rabbitTemplate, new EventsProperties(3, 2, Duration.ofSeconds(5)),
                new SimpleMeterRegistry());
    }

    @Test
    void sendsStraightAwayWhileTheBrokerIsUp() {
        publisher.publish(EXCHANGE, "item.lost.claimed", "a");

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "item.lost.claimed", "a");
        assertThat(publisher.pendingCount()).isZero();
    }

    @Test
    void buffersAndStopsCallingTheBrokerOnceItFails() {
        willThrow(DOWN).given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        publisher.publish(EXCHANGE, "k", "a");
        publisher.publish(EXCHANGE, "k", "b");

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThat(publisher.pendingCount()).isEqualTo(2);
    }

    @Test
    void keepsTheBufferUntilTheConnectionIsBack() {
        bufferThree();
        given(connectionFactory.createConnection()).willThrow(DOWN);

        publisher.flush();

        verify(rabbitTemplate, never()).invoke(any());
        assertThat(publisher.pendingCount()).isEqualTo(3);
    }

    @Test
    void sendsOneBatchPerFlushInOrder() {
        bufferThree();
        brokerIsBack();

        publisher.flush();

        InOrder order = inOrder(rabbitTemplate);
        order.verify(rabbitTemplate).convertAndSend(EXCHANGE, "k", "a");
        order.verify(rabbitTemplate).convertAndSend(EXCHANGE, "k", "b");
        verify(rabbitTemplate, never()).convertAndSend(EXCHANGE, "k", "c");
        assertThat(publisher.pendingCount()).isEqualTo(1);

        publisher.flush();

        verify(rabbitTemplate).convertAndSend(EXCHANGE, "k", "c");
        assertThat(publisher.pendingCount()).isZero();
    }

    @Test
    void putsTheUnsentPartOfABatchBackAtTheHead() {
        bufferThree();
        brokerIsBack();
        willThrow(DOWN).given(rabbitTemplate).convertAndSend(EXCHANGE, "k", "b");

        publisher.flush();
        assertThat(publisher.pendingCount()).isEqualTo(2);

        willDoNothing().given(rabbitTemplate).convertAndSend(EXCHANGE, "k", "b");
        publisher.flush();

        InOrder order = inOrder(rabbitTemplate);
        order.verify(rabbitTemplate).convertAndSend(EXCHANGE, "k", "a");
        order.verify(rabbitTemplate, times(2)).convertAndSend(EXCHANGE, "k", "b");
        order.verify(rabbitTemplate).convertAndSend(EXCHANGE, "k", "c");
    }

    @Test
    void dropsEventsBeyondTheBufferCapacity() {
        bufferThree();

        publisher.publish(EXCHANGE, "k", "d");

        assertThat(publisher.pendingCount()).isEqualTo(3);
    }

    private void bufferThree() {
        willThrow(DOWN).given(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("k"), eq("a"));
        publisher.publish(EXCHANGE, "k", "a");
        publisher.publish(EXCHANGE, "k", "b");
        publisher.publish(EXCHANGE, "k", "c");
        willDoNothing().given(rabbitTemplate).convertAndSend(EXCHANGE, "k", "a");
    }

    private void brokerIsBack() {
        given(connection.isOpen()).willReturn(true);
        given(connectionFactory.createConnection()).willReturn(connection);
    }
}
