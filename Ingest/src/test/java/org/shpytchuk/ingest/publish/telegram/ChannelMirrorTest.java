package org.shpytchuk.ingest.publish.telegram;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.config.events.EventsProperties;
import org.shpytchuk.ingest.event.ItemCreatedEvent;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.pipeline.IngestMetrics;
import org.shpytchuk.ingest.pipeline.PostLedger;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChannelMirrorTest {

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8};
    private static final ItemCreatedEvent EVENT = new ItemCreatedEvent(7L, LocalDate.of(2026, 10, 9), "KEYS",
            "Площа Ринок", "Ключі", "Зв'язка з брелоком", 200, "UAH", "items/2026/10/keys.jpg", "admin");

    private final ChannelPoster poster = mock(ChannelPoster.class);
    private final PostLedger ledger = mock(PostLedger.class);
    private final StoredPhotos photos = mock(StoredPhotos.class);
    private final IngestMetrics metrics = mock(IngestMetrics.class);

    @Test
    void doesNothingWhileTheChannelIsNotAPublisher() {
        mirror(List.of("review")).created(ItemKind.FOUND, EVENT);
        mirror(List.of("review")).removed(ItemKind.FOUND, 7L, "archived");

        verifyNoInteractions(poster, ledger);
    }

    @Test
    void leavesItemsFromSourceChatsToThePipeline() {
        when(ledger.owns(ItemKind.FOUND, 7L)).thenReturn(true);

        mirror(List.of("site", "channel")).created(ItemKind.FOUND, EVENT);

        verify(poster, never()).postFromSite(any(), any(), anyLong(), any());
    }

    @Test
    void waitsForThePipelineToFinishPublishingBeforeItDecides() {
        when(ledger.publishing()).thenReturn(true, true, false);
        when(ledger.owns(ItemKind.FOUND, 7L)).thenReturn(true);

        mirror(List.of("site", "channel")).created(ItemKind.FOUND, EVENT);

        verify(ledger, times(3)).publishing();
        verify(poster, never()).postFromSite(any(), any(), anyLong(), any());
    }

    @Test
    void postsAnItemFromTheSiteWithWhatTheEventCarries() {
        when(photos.load("items/2026/10/keys.jpg")).thenReturn(PHOTO);

        mirror(List.of("site", "channel")).created(ItemKind.FOUND, EVENT);

        ArgumentCaptor<ChannelPost> post = ArgumentCaptor.forClass(ChannelPost.class);
        verify(poster).postFromSite(eq("admin"), eq(ItemKind.FOUND), eq(7L), post.capture());
        ChannelPost built = post.getValue();
        assertThat(built.title()).isEqualTo("Ключі");
        assertThat(built.placeName()).isEqualTo("Площа Ринок");
        assertThat(built.compensation()).isEqualTo(new Money(200, "UAH"));
        assertThat(built.photo()).isEqualTo(PHOTO);
        assertThat(built.itemUrl()).isEqualTo("https://derechi.site/uk/found/7");
        assertThat(built.category()).isEqualTo("KEYS");
        assertThat(built.city()).isNull();
        verify(metrics).channel("posted", "ok");
    }

    @Test
    void deletesTheMessageOfAnArchivedItem() {
        when(poster.delete(ItemKind.LOST, 7L)).thenReturn(true);

        mirror(List.of("site", "channel")).removed(ItemKind.LOST, 7L, "archived");

        verify(metrics).channel("deleted", "ok");
    }

    @Test
    void aMessageTheBotCannotDeleteOnlyCountsAsAFailure() {
        when(poster.delete(ItemKind.LOST, 7L))
                .thenThrow(new IngestException("Bot API deleteMessage failed: HTTP 400 message can't be deleted"));

        assertThatCode(() -> mirror(List.of("site", "channel")).removed(ItemKind.LOST, 7L, "deleted"))
                .doesNotThrowAnyException();

        verify(metrics).channel("deleted", "failed");
    }

    private ChannelMirror mirror(List<String> publishers) {
        IngestProperties ingest = new IngestProperties(publishers, "key", null, 0, null, "https://derechi.site", "uk",
                0.6);
        BotProperties bot = new BotProperties(null, "123:token", "@derechi", "", "uk", null, 0, null);
        EventsProperties events = new EventsProperties("ingest.queue", Duration.ofSeconds(5), Duration.ofMillis(1));
        return new ChannelMirror(poster, ledger, photos, ingest, bot, events, metrics, Clock.systemUTC());
    }
}
