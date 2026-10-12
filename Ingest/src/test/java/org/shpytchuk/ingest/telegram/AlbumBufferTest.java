package org.shpytchuk.ingest.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlbumBufferTest {

    private static final Duration WAIT = Duration.ofSeconds(3);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
    private static final SourceSettings SETTINGS = new SourceSettings("UA", null, "uk");

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<Runnable> timers = new ArrayList<>();
    private final List<IncomingPost> received = new ArrayList<>();
    private final AlbumBuffer buffer = new AlbumBuffer(WAIT, scheduler, received::add);

    @BeforeEach
    void captureTimers() {
        when(scheduler.getClock()).thenReturn(CLOCK);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            timers.add(call.getArgument(0));
            return null;
        });
    }

    @Test
    void messageOutsideAnAlbumGoesStraightThrough() {
        IncomingPost post = post(1, 1, 10);

        buffer.add(0, post);

        assertThat(received).containsExactly(post);
        assertThat(timers).isEmpty();
    }

    @Test
    void partsOfAnAlbumArriveAsOneMergedPostWhenTheLastTimerFires() {
        buffer.add(77, post(1, 12, 2));
        buffer.add(77, post(1, 11, 1));
        buffer.add(77, post(1, 13, 3));

        verify(scheduler, times(3)).schedule(any(Runnable.class), eq(CLOCK.instant().plus(WAIT)));
        assertThat(received).isEmpty();

        timers.getLast().run();

        assertThat(received).singleElement().satisfies(album -> {
            assertThat(MessageMapper.messageId(album)).isEqualTo(11);
            assertThat(album.photos()).extracting(PhotoRef::id).containsExactly("1", "2", "3");
        });
    }

    @Test
    void theWaitCountsFromTheLastPart() {
        buffer.add(77, post(1, 11, 1));
        buffer.add(77, post(1, 12, 2));

        timers.getFirst().run();
        assertThat(received).isEmpty();

        timers.getLast().run();
        assertThat(received).singleElement().satisfies(album -> assertThat(album.photos()).hasSize(2));
    }

    @Test
    void anEarlierTimerFiringLateDoesNotSendTheAlbumAgain() {
        buffer.add(77, post(1, 11, 1));
        buffer.add(77, post(1, 12, 2));

        timers.getLast().run();
        timers.getFirst().run();

        assertThat(received).hasSize(1);
    }

    @Test
    void albumsOfDifferentChatsWithTheSameIdStayApart() {
        buffer.add(77, post(1, 11, 1));
        buffer.add(77, post(2, 11, 2));

        timers.forEach(Runnable::run);

        assertThat(received).extracting(MessageMapper::chatId).containsExactly(1L, 2L);
        assertThat(received).allSatisfy(post -> assertThat(post.photos()).hasSize(1));
    }

    private static IncomingPost post(long chatId, long messageId, int fileId) {
        return new IncomingPost(new PostKey("telegram", Long.toString(chatId), Long.toString(messageId)), "Канал", null,
                Instant.ofEpochSecond(1_000), "", List.of(new PhotoRef(Integer.toString(fileId), 800, 600)), SETTINGS,
                new TelegramDetails(chatId, messageId, "chan", null, List.of(), List.of()));
    }
}
