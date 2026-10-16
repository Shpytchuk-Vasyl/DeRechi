package org.shpytchuk.ingest.publish.telegram;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class CaptionBuilder {

    public static final int PHOTO_LIMIT = 1024;
    public static final int TEXT_LIMIT = 4096;

    private static final String ELLIPSIS = "…";
    private static final String DESCRIPTION_SEPARATOR = "\n\n";
    private static final String CATEGORY_HASHTAG = "channel.hashtag.category.";
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSourceAccessor messages;

    public CaptionBuilder(MessageSource messages, BotProperties properties) {
        this.messages = new MessageSourceAccessor(messages, Locale.forLanguageTag(properties.locale()));
    }

    public String caption(ChannelPost post, int limit) {
        boolean lost = post.kind() == ItemKind.LOST;
        String label = lost ? "🔴 " : "🟢 ";
        String details = details(post);
        String footer = footer(post, lost);
        int fixed = label.length() + details.length() + footer.length();

        String title = escape(post.title());
        String description = "";
        int descriptionRoom = limit - fixed - title.length() - DESCRIPTION_SEPARATOR.length();
        if (descriptionRoom >= 0) {
            description = post.description() == null ? "" : fit(post.description().strip(), descriptionRoom);
        } else {
            title = fit(post.title(), limit - fixed);
            title = title.isEmpty() ? ELLIPSIS : title;
        }

        StringBuilder html = new StringBuilder(limit).append(label).append(title).append(details);
        if (!description.isEmpty()) {
            html.append(DESCRIPTION_SEPARATOR).append(description);
        }
        return html.append(footer).toString();
    }

    private String details(ChannelPost post) {
        StringBuilder html = new StringBuilder()
                .append("\n📍 ").append(escape(post.placeName()))
                .append("\n📅 ").append(post.date().format(DATE));
        Money compensation = post.compensation();
        if (compensation != null && compensation.amount() == 0) {
            html.append("\n💰 ").append(text("channel.reward"));
        } else if (compensation != null) {
            html.append("\n💰 ").append(text("channel.reward")).append(": ").append(compensation.amount());
            if (compensation.currency() != null) {
                html.append(' ').append(escape(compensation.currency()));
            }
        }
        return html.toString();
    }

    private String footer(ChannelPost post, boolean lost) {
        StringBuilder html = new StringBuilder("\n\n")
                .append(text(lost ? "channel.lost.cta" : "channel.found.cta")).append(' ').append(escape(post.itemUrl()));
        String hashtags = hashtags(post);
        if (!hashtags.isEmpty()) {
            html.append("\n\n").append(hashtags);
        }
        return html.toString();
    }

    private String hashtags(ChannelPost post) {
        String kind = messages.getMessage(post.kind() == ItemKind.LOST ? "channel.hashtag.lost" : "channel.hashtag.found");
        String category = post.category() == null ? null
                : messages.getMessage(CATEGORY_HASHTAG + post.category(), (String) null);
        return Stream.of(post.city(), kind, category)
                .map(CaptionBuilder::hashtag)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    static @Nullable String hashtag(@Nullable String word) {
        if (word == null) {
            return null;
        }
        String tag = NON_WORD.matcher(word.strip()).replaceAll("_").replaceAll("^_+|_+$", "");
        return tag.isEmpty() ? null : "#" + tag;
    }

    private String text(String key) {
        return escape(messages.getMessage(key));
    }

    static String escape(@Nullable String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    static String fit(String text, int room) {
        String escaped = escape(text);
        if (escaped.length() <= room) {
            return escaped;
        }
        if (room <= ELLIPSIS.length()) {
            return "";
        }
        StringBuilder cut = new StringBuilder(room).append(escaped, 0, room - ELLIPSIS.length());
        int entity = cut.lastIndexOf("&");
        if (entity > cut.lastIndexOf(";")) {
            cut.setLength(entity);
        }
        if (!cut.isEmpty() && Character.isHighSurrogate(cut.charAt(cut.length() - 1))) {
            cut.setLength(cut.length() - 1);
        }
        int space = lastWhitespace(cut);
        if (space > cut.length() / 2) {
            cut.setLength(space);
        }
        while (!cut.isEmpty() && Character.isWhitespace(cut.charAt(cut.length() - 1))) {
            cut.setLength(cut.length() - 1);
        }
        return cut.isEmpty() ? "" : cut.append(ELLIPSIS).toString();
    }

    private static int lastWhitespace(CharSequence text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
