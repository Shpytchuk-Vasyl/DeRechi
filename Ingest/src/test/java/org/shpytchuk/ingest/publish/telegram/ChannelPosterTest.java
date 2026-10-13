package org.shpytchuk.ingest.publish.telegram;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChannelPosterTest {

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8};

    private final BotApiClient bot = mock(BotApiClient.class);
    private final CaptionBuilder captions = CaptionBuilderTest.builder("uk");

    @Test
    void postsPhotoWithCaption() {
        when(bot.sendPhoto(anyString(), any(), anyString())).thenReturn(11L);
        ChannelPost post = post(PHOTO, "https://derechi.site/uk/found/7");

        long id = poster(properties("@derechi", "")).post(post);

        assertThat(id).isEqualTo(11);
        verify(bot).sendPhoto(eq("@derechi"), eq(PHOTO), startsWith("🟢 Ключі"));
        verify(bot, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void postsTextWhenThereIsNoPhoto() {
        when(bot.sendMessage(anyString(), anyString())).thenReturn(12L);

        long id = poster(properties("@derechi", "")).post(post(null, "https://derechi.site/uk/found/7"));

        assertThat(id).isEqualTo(12);
        verify(bot).sendMessage(eq("@derechi"), startsWith("🟢 Ключі"));
    }

    @Test
    void withoutChannelItIsUnavailable() {
        assertThatThrownBy(() -> poster(properties("", "review")).post(post(PHOTO, "https://x")))
                .isInstanceOf(StageUnavailableException.class);
        verifyNoInteractions(bot);
    }

    @Test
    void failurePropagates() {
        when(bot.sendMessage(anyString(), anyString())).thenThrow(new IngestException("Bot API sendMessage failed"));

        assertThatThrownBy(() -> poster(properties("@derechi", "")).post(post(null, "https://x")))
                .isInstanceOf(IngestException.class);
    }

    private ChannelPoster poster(BotProperties properties) {
        return new ChannelPoster(bot, captions, properties);
    }

    private static BotProperties properties(String publishChat, String reviewChat) {
        return new BotProperties(null, "123:token", publishChat, reviewChat, "uk", null, 0, null);
    }

    private static ChannelPost post(byte[] photo, String url) {
        return new ChannelPost(ItemKind.FOUND, "Ключі", "Зв'язка з брелоком", "Площа Ринок", LocalDate.of(2026, 10, 9),
                new Money(200, "UAH"), photo, url, "Загублено & знайдено");
    }
}
