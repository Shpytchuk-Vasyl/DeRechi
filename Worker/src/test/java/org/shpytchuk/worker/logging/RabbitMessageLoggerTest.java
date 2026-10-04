package org.shpytchuk.worker.logging;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitMessageLoggerTest {

    private final RabbitMessageLogger logger = new RabbitMessageLogger();

    @Test
    void describesTheQueueTheKeyTheTypeAndTheBody() {
        assertThat(RabbitMessageLogger.describe(message(false)))
                .isEqualTo("worker.claims item.lost.claimed CLAIM {\"id\":42}");
        assertThat(RabbitMessageLogger.describe(message(true))).endsWith(" (redelivered)");
    }

    @Test
    void passesTheResultThrough() throws Throwable {
        MethodInvocation invocation = invocation(message(false));
        when(invocation.proceed()).thenReturn("done");

        assertThat(logger.invoke(invocation)).isEqualTo("done");
    }

    @Test
    void rethrowsTheFailureUntouched() throws Throwable {
        MethodInvocation invocation = invocation(message(false));
        AmqpRejectAndDontRequeueException failure = new AmqpRejectAndDontRequeueException("No handler");
        when(invocation.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> logger.invoke(invocation)).isSameAs(failure);
    }

    @Test
    void leavesAnythingButASingleMessageAlone() throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{"channel"});
        when(invocation.proceed()).thenReturn("done");

        assertThat(logger.invoke(invocation)).isEqualTo("done");
    }

    private static MethodInvocation invocation(Message message) {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{null, message});
        return invocation;
    }

    private static Message message(boolean redelivered) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("worker.claims");
        properties.setReceivedRoutingKey("item.lost.claimed");
        properties.setHeader("__TypeId__", "CLAIM");
        properties.setRedelivered(redelivered);
        return new Message("{\"id\":42}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
