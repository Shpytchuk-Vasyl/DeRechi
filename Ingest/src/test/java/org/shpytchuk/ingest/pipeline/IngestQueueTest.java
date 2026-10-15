package org.shpytchuk.ingest.pipeline;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.exception.BudgetExceededException;
import org.shpytchuk.ingest.exception.OpenRouterUnavailableException;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PostSources;
import org.shpytchuk.ingest.source.telegram.TelegramSource;
import org.shpytchuk.ingest.support.Posts;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestQueueTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-10-11T00:00:00Z");
    private static final IncomingPost POST = Posts.telegram(10L << 20, "Загубив гаманець 067 123 45 67", List.of(), NOW);
    private static final OpenRouterUnavailableException UNAVAILABLE =
            new OpenRouterUnavailableException("OpenRouter did not take the request: HTTP 503");

    private final IngestPipeline pipeline = mock(IngestPipeline.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    {
        when(scheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IngestQueue queue = new IngestQueue(new PostSources(List.of()), pipeline, mock(PostLedger.class),
            new IngestMetrics(registry, Clock.systemUTC(), mock(TelegramSource.class)), registry, scheduler);

    @Test
    void anOverBudgetPostComesBackWhenTheLimitResets() {
        doThrow(new BudgetExceededException("The daily limit is used up", NEXT_DAY)).when(pipeline).process(POST);

        queue.handle(POST);

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(retry.capture(), eq(NEXT_DAY));
        assertThat(gauge("derechi.ingest.postponed")).isEqualTo(1);
        assertThat(registry.get("derechi.ingest.posts").tag("outcome", "budget").counter().count()).isEqualTo(1);

        retry.getValue().run();

        assertThat(gauge("derechi.ingest.postponed")).isZero();
        assertThat(gauge("derechi.ingest.queue")).isEqualTo(1);
    }

    @Test
    void aPostponedPostIsNotQueuedAgainBeforeItsTime() {
        doThrow(new BudgetExceededException("The daily limit is used up", NEXT_DAY)).when(pipeline).process(POST);
        queue.handle(POST);

        queue.offer(POST);
        queue.handle(POST);

        assertThat(gauge("derechi.ingest.queue")).isZero();
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void anUnavailableStageIsRetriedTenMinutesLater() {
        doThrow(UNAVAILABLE).when(pipeline).process(POST);

        queue.handle(POST);

        verify(scheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(10))));
        assertThat(gauge("derechi.ingest.postponed")).isEqualTo(1);
        assertThat(registry.get("derechi.ingest.posts").tag("outcome", "unavailable").counter().count()).isEqualTo(1);
    }

    @Test
    void afterThreeRetriesThePostIsDropped() {
        doThrow(UNAVAILABLE).when(pipeline).process(POST);

        retryThreeTimes();
        queue.handle(POST);

        verify(scheduler, times(3)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(gauge("derechi.ingest.postponed")).isZero();
    }

    @Test
    void aPostThatGoesThroughGetsItsRetriesBack() {
        doThrow(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE).doNothing().doThrow(UNAVAILABLE).when(pipeline).process(POST);

        retryThreeTimes();
        queue.handle(POST);
        queue.handle(POST);

        verify(scheduler, times(4)).schedule(any(Runnable.class), any(Instant.class));
    }

    private void retryThreeTimes() {
        for (int retry = 0; retry < 3; retry++) {
            queue.handle(POST);
            ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, atLeastOnce()).schedule(scheduled.capture(), any(Instant.class));
            scheduled.getValue().run();
        }
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
