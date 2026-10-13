package org.shpytchuk.ingest.source.telegram;

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

class PostBufferTest {

    private static final Duration WAIT = Duration.ofSeconds(3);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);
    private static final SourceSettings SETTINGS = new SourceSettings("UA", null, "uk");
    private static final String AUTHOR = "user 555";

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<Runnable> timers = new ArrayList<>();
    private final List<IncomingPost> received = new ArrayList<>();
    private final PostBuffer buffer = new PostBuffer(WAIT, scheduler, received::add);

    @BeforeEach
    void captureTimers() {
        when(scheduler.getClock()).thenReturn(CLOCK);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(call -> {
            timers.add(call.getArgument(0));
            return null;
        });
    }

    @Test
    void aSingleMessageGoesOnAfterTheWait() {
        MessagePart part = text(1, 10, AUTHOR, "Знайдено ключі", 1_000);

        buffer.add(part);
        assertThat(received).isEmpty();

        timers.getLast().run();
        assertThat(received).containsExactly(part.post());
    }

    @Test
    void partsOfAnAlbumArriveAsOneMergedPostWhenTheLastTimerFires() {
        buffer.add(photo(1, 12, AUTHOR, 77, 2, 1_000));
        buffer.add(photo(1, 11, AUTHOR, 77, 1, 1_000));
        buffer.add(photo(1, 13, AUTHOR, 77, 3, 1_000));

        verify(scheduler, times(3)).schedule(any(Runnable.class), eq(CLOCK.instant().plus(WAIT)));
        assertThat(received).isEmpty();

        timers.getLast().run();

        assertThat(received).singleElement().satisfies(album -> {
            assertThat(MessageMapper.messageId(album)).isEqualTo(11);
            assertThat(album.photos()).extracting(PhotoRef::id).containsExactly("1", "2", "3");
        });
    }

    @Test
    void photosSentRightAfterATextBelongToIt() {
        buffer.add(text(1, 10, AUTHOR, "Знайшов леокард", 1_000));
        buffer.add(photo(1, 11, AUTHOR, 77, 1, 1_000));
        buffer.add(photo(1, 12, AUTHOR, 77, 2, 1_001));

        timers.getLast().run();

        assertThat(received).singleElement().satisfies(post -> {
            assertThat(MessageMapper.messageId(post)).isEqualTo(10);
            assertThat(post.text()).isEqualTo("Знайшов леокард");
            assertThat(post.photos()).extracting(PhotoRef::id).containsExactly("1", "2");
        });
    }

    @Test
    void aSecondTextStartsANewPostAndSendsTheFirstAtOnce() {
        MessagePart first = text(1, 10, AUTHOR, "Загублено паспорт", 1_000);
        MessagePart second = text(1, 11, AUTHOR, "Загублено гаманець", 1_000);

        buffer.add(first);
        buffer.add(second);
        assertThat(received).containsExactly(first.post());

        timers.forEach(Runnable::run);
        assertThat(received).containsExactly(first.post(), second.post());
    }

    @Test
    void photosOfAnotherSenderStayApart() {
        buffer.add(text(1, 10, AUTHOR, "Знайшов леокард", 1_000));
        buffer.add(photo(1, 11, "user 777", 0, 1, 1_000));

        timers.forEach(Runnable::run);

        assertThat(received).hasSize(2).allSatisfy(post -> assertThat(MessageMapper.messageId(post)).isIn(10L, 11L));
    }

    @Test
    void photosPostedLaterThanTheWaitStayApart() {
        buffer.add(text(1, 10, AUTHOR, "Знайшов леокард", 1_000));
        buffer.add(photo(1, 11, AUTHOR, 0, 1, 1_004));

        assertThat(received).singleElement().satisfies(post -> assertThat(post.photos()).isEmpty());
    }

    @Test
    void theWaitCountsFromTheLastPart() {
        buffer.add(photo(1, 11, AUTHOR, 77, 1, 1_000));
        buffer.add(photo(1, 12, AUTHOR, 77, 2, 1_000));

        timers.getFirst().run();
        assertThat(received).isEmpty();

        timers.getLast().run();
        assertThat(received).singleElement().satisfies(album -> assertThat(album.photos()).hasSize(2));
    }

    @Test
    void anEarlierTimerFiringLateDoesNotSendThePostAgain() {
        buffer.add(photo(1, 11, AUTHOR, 77, 1, 1_000));
        buffer.add(photo(1, 12, AUTHOR, 77, 2, 1_000));

        timers.getLast().run();
        timers.getFirst().run();

        assertThat(received).hasSize(1);
    }

    @Test
    void sameSenderInDifferentChatsStaysApart() {
        buffer.add(photo(1, 11, AUTHOR, 77, 1, 1_000));
        buffer.add(photo(2, 11, AUTHOR, 77, 2, 1_000));

        timers.forEach(Runnable::run);

        assertThat(received).extracting(MessageMapper::chatId).containsExactly(1L, 2L);
        assertThat(received).allSatisfy(post -> assertThat(post.photos()).hasSize(1));
    }

    private static MessagePart text(long chatId, long messageId, String sender, String text, long epochSecond) {
        return new MessagePart(post(chatId, messageId, text, List.of(), epochSecond), sender, 0);
    }

    private static MessagePart photo(long chatId, long messageId, String sender, long albumId, int fileId,
                                     long epochSecond) {
        return new MessagePart(post(chatId, messageId, "", List.of(new PhotoRef(Integer.toString(fileId), 800, 600)),
                epochSecond), sender, albumId);
    }

    private static IncomingPost post(long chatId, long messageId, String text, List<PhotoRef> photos, long epochSecond) {
        return new IncomingPost(new PostKey("telegram", Long.toString(chatId), Long.toString(messageId)), "Канал", null,
                Instant.ofEpochSecond(epochSecond), text, photos, SETTINGS,
                new TelegramDetails(chatId, messageId, "chan", null, List.of(), List.of()));
    }
}
