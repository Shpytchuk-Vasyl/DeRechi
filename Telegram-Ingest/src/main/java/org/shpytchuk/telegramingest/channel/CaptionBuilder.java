package org.shpytchuk.telegramingest.channel;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.config.BotProperties;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Money;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class CaptionBuilder {

    public static final int PHOTO_LIMIT = 1024;
    public static final int TEXT_LIMIT = 4096;

    private static final String ELLIPSIS = "…";
    private static final String DESCRIPTION_SEPARATOR = "\n\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSourceAccessor messages;

    public CaptionBuilder(MessageSource messages, BotProperties properties) {
        this.messages = new MessageSourceAccessor(messages, Locale.forLanguageTag(properties.locale()));
    }

    public String caption(ChannelPost post, int limit) {
        boolean lost = post.kind() == ItemKind.LOST;
        String label = new StringBuilder(lost ? "🔴 " : "🟢 ")
                .append(text(lost ? "channel.lost.title" : "channel.found.title")).append(": ").toString();
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
        if (compensation != null) {
            html.append("\n💰 ").append(text("channel.reward")).append(": ").append(compensation.amount());
            if (compensation.currency() != null) {
                html.append(' ').append(escape(compensation.currency()));
            }
        }
        return html.toString();
    }

    private String footer(ChannelPost post, boolean lost) {
        StringBuilder html = new StringBuilder("\n\n");
        if (post.itemUrl() != null) {
            html.append(text(lost ? "channel.lost.cta" : "channel.found.cta")).append(' ').append(escape(post.itemUrl()));
        } else {
            html.append(text("channel.preview.noLink"));
        }
        return html.append('\n').append(text("channel.source")).append(": ").append(escape(post.sourceTitle()))
                .toString();
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
