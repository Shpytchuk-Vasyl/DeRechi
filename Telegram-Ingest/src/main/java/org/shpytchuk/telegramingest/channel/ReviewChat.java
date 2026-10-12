package org.shpytchuk.telegramingest.channel;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.config.BotProperties;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.shpytchuk.telegramingest.channel.CaptionBuilder.PHOTO_LIMIT;
import static org.shpytchuk.telegramingest.channel.CaptionBuilder.TEXT_LIMIT;
import static org.shpytchuk.telegramingest.channel.CaptionBuilder.escape;
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Service
@AllArgsConstructor
@Slf4j
public class ReviewChat {

    private static final String PRE_OPEN = "<pre>";
    private static final String PRE_CLOSE = "</pre>";
    private static final int MIN_JSON = 40;

    private final BotApiClient bot;
    private final CaptionBuilder captions;
    private final BotProperties properties;
    private final JsonMapper jsonMapper;

    public void send(Preview preview) {
        if (!properties.canPreview()) {
            log.debug("Skipping {} preview: bot token or review chat is not configured", preview.outcome());
            return;
        }
        try {
            String header = header(preview);
            if (preview.photos().isEmpty()) {
                bot.sendMessage(properties.reviewChat(), withJson(withCaption(header, preview.post(), TEXT_LIMIT), preview));
                return;
            }
            bot.sendPhoto(properties.reviewChat(), preview.photos().getFirst(),
                    withCaption(header, preview.post(), PHOTO_LIMIT));
            if (preview.extractionJson() != null) {
                bot.sendMessage(properties.reviewChat(), withJson(header, preview));
            }
        } catch (RuntimeException e) {
            log.warn("Preview of a {} post failed: {}", preview.outcome(), getMostSpecificCause(e).toString());
        }
    }

    private static String header(Preview preview) {
        StringBuilder header = new StringBuilder("<b>DRY_RUN · ").append(escape(preview.outcome())).append("</b>\n")
                .append(escape(preview.sourceTitle()));
        if (preview.sourceLink() != null) {
            header.append('\n').append(escape(preview.sourceLink()));
        }
        return header.toString();
    }

    private String withCaption(String header, @Nullable ChannelPost post, int limit) {
        if (post == null) {
            return header;
        }
        int wouldBe = post.photo() != null ? PHOTO_LIMIT : TEXT_LIMIT;
        int room = limit - header.length() - 2;
        return header + "\n\n" + captions.caption(post, Math.min(wouldBe, room));
    }

    private String withJson(String head, Preview preview) {
        StringBuilder text = new StringBuilder(head);
        int jsonRoom = TEXT_LIMIT - text.length() - 2 - PRE_OPEN.length() - PRE_CLOSE.length();
        if (preview.extractionJson() != null && jsonRoom >= MIN_JSON) {
            text.append("\n\n").append(PRE_OPEN)
                    .append(CaptionBuilder.fit(pretty(preview.extractionJson()), jsonRoom))
                    .append(PRE_CLOSE);
        }
        return text.toString();
    }

    private String pretty(String json) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMapper.readTree(json))
                    .replace("\r\n", "\n");
        } catch (JacksonException notJson) {
            return json;
        }
    }
}
