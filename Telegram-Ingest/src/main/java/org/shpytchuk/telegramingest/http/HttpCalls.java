package org.shpytchuk.telegramingest.http;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Slf4j
public final class HttpCalls {

    private static final int MAX_DELAY_FACTOR = 10;

    private HttpCalls() {
    }

    public static RestClient.Builder restClient(Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory);
    }

    public static RetryTemplate retryTemplate(int maxAttempts, Duration delay, Predicate<Throwable> retryable) {
        RetryTemplate template = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(maxAttempts - 1)
                .delay(delay)
                .multiplier(2)
                .maxDelay(delay.multipliedBy(MAX_DELAY_FACTOR))
                .predicate(retryable)
                .build());
        template.setRetryListener(new RetryListener() {
            @Override
            public void beforeRetry(RetryPolicy policy, Retryable<?> operation, RetryState state) {
                log.warn("Retrying {} (retry {}): {}", operation.getName(), state.getRetryCount(),
                        describe(state.getLastException()));
            }
        });
        return template;
    }

    public static <T> T execute(RetryTemplate template, String operation, Supplier<T> call) {
        try {
            return template.execute(new Retryable<>() {
                @Override
                public T execute() {
                    return call.get();
                }

                @Override
                public String getName() {
                    return operation;
                }
            });
        } catch (RetryException e) {
            if (e.getCause() instanceof RuntimeException last) {
                throw last;
            }
            throw new IngestException(operation + " failed: " + describe(e.getCause()));
        }
    }

    public static boolean transientFailure(Throwable e) {
        Integer status = status(e);
        if (status != null) {
            return status >= 500 || status == 408 || status == 429;
        }
        return e instanceof ResourceAccessException;
    }

    public static boolean notDelivered(Throwable e) {
        Integer status = status(e);
        if (status != null) {
            return status == 429 || status == 502 || status == 503;
        }
        Throwable cause = getMostSpecificCause(e);
        return cause instanceof ConnectException || cause instanceof UnknownHostException
                || cause instanceof HttpConnectTimeoutException;
    }

    public static String describe(@Nullable Throwable e) {
        if (e == null) {
            return "unknown failure";
        }
        Integer status = status(e);
        return status != null ? "HTTP " + status : getMostSpecificCause(e).getClass().getName();
    }

    private static @Nullable Integer status(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                return response.getStatusCode().value();
            }
        }
        return null;
    }
}
