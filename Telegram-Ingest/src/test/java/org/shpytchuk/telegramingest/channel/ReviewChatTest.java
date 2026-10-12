package org.shpytchuk.telegramingest.channel;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.telegramingest.config.BotProperties;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Money;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewChatTest {

    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8};
    private static final String JSON = "{\"isNotice\":true,\"title\":\"Гаманець <чорний>\",\"confidence\":0.9}";

    private final BotApiClient bot = mock(BotApiClient.class);
    private final CaptionBuilder captions = CaptionBuilderTest.builder("uk");

    @Test
    void withoutReviewChatItDoesNothing() {
        reviewChat(properties("@derechi", "")).send(preview(post(PHOTO, null), JSON, List.of(PHOTO)));

        verifyNoInteractions(bot);
    }

    @Test
    void sendsFirstPhotoWithTheChannelCaptionThenTheJson() {
        byte[] second = {3};
        String header = "<b>DRY_RUN · previewed</b>\nЗагублено &amp; знайдено\nhttps://t.me/lost/5";
        reviewChat(properties("", "-500")).send(preview(post(PHOTO, null), JSON, List.of(PHOTO, second)));

        ArgumentCaptor<String> caption = ArgumentCaptor.forClass(String.class);
        verify(bot).sendPhoto(eq("-500"), eq(PHOTO), caption.capture());
        verify(bot, never()).sendPhoto(anyString(), eq(second), anyString());
        assertThat(caption.getValue())
                .startsWith(header + "\n\n🟢 Знайдено: Ключі")
                .contains("Посилання ще немає")
                .doesNotContain("<pre>");
        assertThat(caption.getValue().length()).isLessThanOrEqualTo(CaptionBuilder.PHOTO_LIMIT);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq("-500"), text.capture());
        assertThat(text.getValue())
                .startsWith(header + "\n\n<pre>{\n")
                .contains("\"title\" : \"Гаманець &lt;чорний&gt;\"")
                .doesNotContain("Знайдено: Ключі")
                .endsWith("}</pre>");
    }

    @Test
    void photoWithoutJsonIsOneMessage() {
        reviewChat(properties("", "-500")).send(preview(post(PHOTO, null), null, List.of(PHOTO)));

        verify(bot).sendPhoto(eq("-500"), eq(PHOTO), startsWith("<b>DRY_RUN · previewed</b>"));
        verify(bot, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void textOnlyPreviewKeepsTheCaptionAndTheJsonInOneMessage() {
        reviewChat(properties("", "-500")).send(preview(post(null, null), JSON, List.of()));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq("-500"), text.capture());
        verify(bot, never()).sendPhoto(anyString(), any(), anyString());
        assertThat(text.getValue()).contains("Знайдено: Ключі").endsWith("}</pre>");
    }

    @Test
    void skippedPreviewHasNoCaptionAndNoPhoto() {
        reviewChat(properties("", "-500")).send(new Preview("Chat", null, "not_notice", null, JSON, List.of()));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq("-500"), text.capture());
        verify(bot, never()).sendPhoto(anyString(), any(), anyString());
        assertThat(text.getValue()).startsWith("<b>DRY_RUN · not_notice</b>\nChat\n\n<pre>").doesNotContain("Знайдено");
    }

    @Test
    void hugeJsonIsCutToFitTheMessage() {
        String json = "{\"description\":\"" + "<&>".repeat(3000) + "\"}";
        reviewChat(properties("", "-500")).send(preview(post(null, null), json, List.of()));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(eq("-500"), text.capture());
        assertThat(text.getValue().length()).isLessThanOrEqualTo(CaptionBuilder.TEXT_LIMIT).isGreaterThan(CaptionBuilder.TEXT_LIMIT - 20);
        assertThat(text.getValue()).endsWith("…</pre>").doesNotContain("<&>");
    }

    @Test
    void invalidJsonIsShownAsIs() {
        reviewChat(properties("", "-500")).send(new Preview("Chat", null, "failed", null, "not json <x>", List.of()));

        verify(bot).sendMessage(eq("-500"), eq("<b>DRY_RUN · failed</b>\nChat\n\n<pre>not json &lt;x&gt;</pre>"));
    }

    @Test
    void failuresAreSwallowed() {
        when(bot.sendPhoto(anyString(), any(), anyString())).thenThrow(new IngestException("Bot API sendPhoto rejected"));

        assertThatCode(() -> reviewChat(properties("", "-500")).send(preview(post(PHOTO, null), JSON, List.of(PHOTO))))
                .doesNotThrowAnyException();
    }

    private ReviewChat reviewChat(BotProperties properties) {
        return new ReviewChat(bot, captions, properties, JsonMapper.builder().build());
    }

    private static BotProperties properties(String publishChat, String reviewChat) {
        return new BotProperties(null, "123:token", publishChat, reviewChat, "uk", null, 0, null);
    }

    private static ChannelPost post(byte[] photo, String url) {
        return new ChannelPost(ItemKind.FOUND, "Ключі", "Зв'язка з брелоком", "Площа Ринок", LocalDate.of(2026, 10, 9),
                new Money(200, "UAH"), photo, url, "Загублено & знайдено");
    }

    private static Preview preview(ChannelPost post, String json, List<byte[]> photos) {
        return new Preview("Загублено & знайдено", "https://t.me/lost/5", "previewed", post, json, photos);
    }
}
