package org.shpytchuk.ingest.publish.telegram;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.ingest.publish.telegram.CaptionBuilder.PHOTO_LIMIT;
import static org.shpytchuk.ingest.publish.telegram.CaptionBuilder.TEXT_LIMIT;

class CaptionBuilderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 9);

    private final CaptionBuilder uk = builder("uk");

    static CaptionBuilder builder(String locale) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new CaptionBuilder(messages, new BotProperties(null, "t", "@c", "", locale, null, 0, null));
    }

    @Test
    void lostCaptionInUkrainian() {
        ChannelPost post = new ChannelPost(ItemKind.LOST, "Чорний гаманець", "Шкіряний, з карткою ПриватБанку",
                "ТЦ Ocean Plaza", DATE, new Money(500, "UAH"), null, "https://derechi.site/uk/lost/42", "Бюро знахідок Київ");

        assertThat(uk.caption(post, PHOTO_LIMIT)).isEqualTo("""
                🔴 Загублено: Чорний гаманець
                📍 ТЦ Ocean Plaza
                📅 09.10.2026
                💰 Винагорода: 500 UAH

                Шкіряний, з карткою ПриватБанку

                Знайшли? Залиште свої контакти тут, автор отримає їх: https://derechi.site/uk/lost/42
                Джерело: Бюро знахідок Київ""");
    }

    @Test
    void foundCaptionWithoutRewardAndDescription() {
        ChannelPost post = new ChannelPost(ItemKind.FOUND, "Ключі", null, "Площа Ринок", DATE, null,
                new byte[]{1}, "https://derechi.site/uk/found/7", "Загублено Львів");

        assertThat(uk.caption(post, PHOTO_LIMIT)).isEqualTo("""
                🟢 Знайдено: Ключі
                📍 Площа Ринок
                📅 09.10.2026

                Ваше? Залиште свої контакти тут, автор отримає їх: https://derechi.site/uk/found/7
                Джерело: Загублено Львів""");
    }

    @Test
    void rewardWithoutCurrencyShowsTheAmountOnly() {
        ChannelPost post = post("Сумка", null, new Money(1000, null), "https://x/uk/lost/1");

        assertThat(uk.caption(post, PHOTO_LIMIT)).contains("💰 Винагорода: 1000\n").doesNotContain("null");
    }

    @Test
    void withoutLinkSaysThereIsNoneYet() {
        String caption = uk.caption(post("Сумка", null, null, null), PHOTO_LIMIT);

        assertThat(caption).contains("Посилання ще немає").doesNotContain("Знайшли?");
    }

    @Test
    void escapesEveryValue() {
        ChannelPost post = new ChannelPost(ItemKind.LOST, "<b>Сумка</b> & ключі", "a <script> & b",
                "Cafe <Tom & Jerry>", DATE, new Money(1, "<X>"), null, "https://x/uk/lost/1?a=1&b=2", "Chat <i>&</i>");

        String caption = uk.caption(post, PHOTO_LIMIT);

        assertThat(caption)
                .contains("&lt;b&gt;Сумка&lt;/b&gt; &amp; ключі")
                .contains("a &lt;script&gt; &amp; b")
                .contains("Cafe &lt;Tom &amp; Jerry&gt;")
                .contains("1 &lt;X&gt;")
                .contains("https://x/uk/lost/1?a=1&amp;b=2")
                .contains("Chat &lt;i&gt;&amp;&lt;/i&gt;")
                .doesNotContain("<b>").doesNotContain("<script>").doesNotContain("<i>");
    }

    @Test
    void hasNoLongDashes() {
        String caption = uk.caption(post("Сумка", "Опис", new Money(5, "UAH"), "https://x/uk/lost/1"), PHOTO_LIMIT);
        String preview = uk.caption(post("Сумка", "Опис", null, null), PHOTO_LIMIT);

        assertThat(caption + preview).doesNotContain("—").doesNotContain("–");
    }

    @Test
    void englishIsTheFallback() {
        String caption = builder("de").caption(post("Bag", null, new Money(5, "EUR"), "https://x/de/lost/1"), PHOTO_LIMIT);

        assertThat(caption).startsWith("🔴 Lost: Bag").contains("💰 Reward: 5 EUR").contains("Source: Chat");
    }

    @Test
    void longDescriptionIsShortenedOnAWordBoundaryToFitThePhotoLimit() {
        String description = "слово ".repeat(400).strip();
        ChannelPost post = post("Сумка", description, null, "https://x/uk/lost/1");

        String caption = uk.caption(post, PHOTO_LIMIT);

        assertThat(caption.length()).isLessThanOrEqualTo(PHOTO_LIMIT).isGreaterThan(PHOTO_LIMIT - 20);
        assertThat(caption).contains("слово слово…\n\n").contains("Сумка").endsWith("Джерело: Chat");
    }

    @Test
    void escapingIsCountedInTheLimit() {
        ChannelPost post = post("Сумка", "& ".repeat(600).strip(), null, "https://x/uk/lost/1");

        assertThat(uk.caption(post, PHOTO_LIMIT).length()).isLessThanOrEqualTo(PHOTO_LIMIT);
    }

    @Test
    void textMessagesGetTheLargerLimit() {
        String description = "слово ".repeat(400).strip();
        ChannelPost post = post("Сумка", description, null, "https://x/uk/lost/1");

        assertThat(uk.caption(post, TEXT_LIMIT)).contains(description);
    }

    @Test
    void titleIsShortenedOnlyWhenTheDescriptionIsGone() {
        String title = "Дуже довга назва ".repeat(20).strip();
        ChannelPost post = post(title, "опис", null, "https://x/uk/lost/1");

        String caption = uk.caption(post, 250);

        assertThat(caption.length()).isLessThanOrEqualTo(250);
        assertThat(caption).doesNotContain("опис").contains("Загублено: Дуже довга назва").contains("…\n📍");
    }

    @Test
    void fitKeepsWholeWordsSurrogatePairsAndEntities() {
        assertThat(CaptionBuilder.fit("one two three", 9)).isEqualTo("one two…");
        assertThat(CaptionBuilder.fit("short", 9)).isEqualTo("short");
        assertThat(CaptionBuilder.fit("abcdefghij", 5)).isEqualTo("abcd…");
        assertThat(CaptionBuilder.fit("ab😀cd", 4)).isEqualTo("ab…");
        assertThat(CaptionBuilder.fit("ab<cd", 6)).isEqualTo("ab…");
        assertThat(CaptionBuilder.fit("a&b", 4)).isEqualTo("a…");
    }

    @Test
    void fitGivesNothingWhenOnlyTheEllipsisWouldFit() {
        assertThat(CaptionBuilder.fit("abc", 1)).isEmpty();
        assertThat(CaptionBuilder.fit("<<<", 3)).isEmpty();
    }

    private static ChannelPost post(String title, String description, Money compensation, String url) {
        return new ChannelPost(ItemKind.LOST, title, description, "Місце", DATE, compensation, null, url, "Chat");
    }
}
