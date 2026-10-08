package org.shpytchuk.worker.logging;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

public class RabbitMessageLogger implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RabbitMessageLogger.class);

    private static final String TYPE_HEADER = "__TypeId__";

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object[] arguments = invocation.getArguments();
        if (!log.isWarnEnabled() || arguments.length < 2 || !(arguments[1] instanceof Message message)) {
            return invocation.proceed();
        }

        String received = describe(message);
        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            log.info("Rabbit {} -> OK in {} ms", received, millisSince(start));
            return result;
        } catch (Throwable failure) {
            log.warn("Rabbit {} -> failed in {} ms: {}", received, millisSince(start),
                    getMostSpecificCause(failure).toString());
            throw failure;
        }
    }

    static String describe(Message message) {
        MessageProperties properties = message.getMessageProperties();
        return "%s %s %s %s%s".formatted(
                properties.getConsumerQueue(),
                properties.getReceivedRoutingKey(),
                properties.getHeader(TYPE_HEADER),
                id(message.getBody()),
                Boolean.TRUE.equals(properties.isRedelivered()) ? " (redelivered)" : "");
    }

    private static String id(byte[] body) {
        if (body == null || body.length == 0) {
            return "without body";
        }
        try {
            JsonNode tree = JsonMapper.shared().readTree(body);
            JsonNode id = tree == null ? null : tree.path("id");
            if (id != null && id.isIntegralNumber()) {
                return id.asString();
            }
        } catch (JacksonException unreadable) {
            // not JSON: the conversion fails later and the failure line says why
        }
        return "without id, " + body.length + " bytes";
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
