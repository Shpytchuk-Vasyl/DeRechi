package org.shpytchuk.ingest.publish.telegram;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.pipeline.PostLedger;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChannelPosterTest {

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8};

    private final BotApiClient bot = mock(BotApiClient.class);
    private final CaptionBuilder captions = CaptionBuilderTest.builder("uk");
    private final PostLedger ledger = mock(PostLedger.class);

    @Test
    void postsPhotoWithCaption() {
        when(bot.sendPhoto(anyString(), any(), anyString())).thenReturn(11L);
        ChannelPost post = post(PHOTO, "https://derechi.site/uk/found/7");

        long id = poster(properties("@derechi")).post(post);

        assertThat(id).isEqualTo(11);
        verify(bot).sendPhoto(eq("@derechi"), eq(PHOTO), startsWith("🟢 Ключі"));
        verify(bot, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void postsTextWhenThereIsNoPhoto() {
        when(bot.sendMessage(anyString(), anyString())).thenReturn(12L);

        long id = poster(properties("@derechi")).post(post(null, "https://derechi.site/uk/found/7"));

        assertThat(id).isEqualTo(12);
        verify(bot).sendMessage(eq("@derechi"), startsWith("🟢 Ключі"));
    }

    @Test
    void withoutChannelItIsUnavailable() {
        assertThatThrownBy(() -> poster(properties("")).post(post(PHOTO, "https://x")))
                .isInstanceOf(StageUnavailableException.class);
        verifyNoInteractions(bot);
    }

    @Test
    void failurePropagates() {
        when(bot.sendMessage(anyString(), anyString())).thenThrow(new IngestException("Bot API sendMessage failed"));

        assertThatThrownBy(() -> poster(properties("@derechi")).post(post(null, "https://x")))
                .isInstanceOf(IngestException.class);
    }

    @Test
    void postsAnItemFromTheSiteAndKeepsItsMessage() {
        when(bot.sendMessage(anyString(), anyString())).thenReturn(12L);

        poster(properties("@derechi")).postFromSite("site", ItemKind.FOUND, 7L,
                post(null, "https://derechi.site/uk/found/7"));

        verify(ledger).postedFromSite("site", ItemKind.FOUND, 7L, 12L);
    }

    @Test
    void aFailedPostRecordsNothing() {
        when(bot.sendMessage(anyString(), anyString())).thenThrow(new IngestException("Bot API sendMessage failed"));

        assertThatThrownBy(() -> poster(properties("@derechi")).postFromSite("site", ItemKind.FOUND, 7L,
                post(null, "https://x"))).isInstanceOf(IngestException.class);

        verify(ledger, never()).postedFromSite(any(), any(), anyLong(), anyLong());
    }

    @Test
    void deletesTheMessageOfAnItemAndForgetsIt() {
        when(ledger.messageId(ItemKind.LOST, 7L)).thenReturn(Optional.of(12L));

        assertThat(poster(properties("@derechi")).delete(ItemKind.LOST, 7L)).isTrue();

        verify(bot).deleteMessage("@derechi", 12L);
        verify(ledger).removedFromChannel(ItemKind.LOST, 7L);
    }

    @Test
    void anItemWithoutAMessageHasNothingToDelete() {
        when(ledger.messageId(ItemKind.LOST, 7L)).thenReturn(Optional.empty());

        assertThat(poster(properties("@derechi")).delete(ItemKind.LOST, 7L)).isFalse();

        verifyNoInteractions(bot);
    }

    @Test
    void aMessageTheBotCannotDeleteStaysOnRecord() {
        when(ledger.messageId(ItemKind.LOST, 7L)).thenReturn(Optional.of(12L));
        doThrow(new IngestException("Bot API deleteMessage failed: 400 message can't be deleted"))
                .when(bot).deleteMessage("@derechi", 12L);

        assertThatThrownBy(() -> poster(properties("@derechi")).delete(ItemKind.LOST, 7L))
                .isInstanceOf(IngestException.class);

        verify(ledger, never()).removedFromChannel(any(), anyLong());
    }

    private ChannelPoster poster(BotProperties properties) {
        return new ChannelPoster(bot, captions, properties, ledger);
    }

    private static BotProperties properties(String publishChat) {
        return new BotProperties(null, "123:token", publishChat, "uk", null, 0, null);
    }

    private static ChannelPost post(byte[] photo, String url) {
        return new ChannelPost(ItemKind.FOUND, "Ключі", "Зв'язка з брелоком", "Площа Ринок", LocalDate.of(2026, 10, 9),
                new Money(200, "UAH"), photo, url);
    }
}
