package org.shpytchuk.telegramingest.channel;

import lombok.AllArgsConstructor;
import org.shpytchuk.telegramingest.config.BotProperties;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.springframework.stereotype.Service;

import static org.shpytchuk.telegramingest.channel.CaptionBuilder.PHOTO_LIMIT;
import static org.shpytchuk.telegramingest.channel.CaptionBuilder.TEXT_LIMIT;

@Service
@AllArgsConstructor
public class ChannelPoster {

    private final BotApiClient bot;
    private final CaptionBuilder captions;
    private final BotProperties properties;

    /**
     * @throws StageUnavailableException bot token or channel not configured
     * @throws IngestException           the Bot API refused or could not be reached
     */
    public long post(ChannelPost post) {
        if (!properties.canPublish()) {
            throw new StageUnavailableException("Bot token or publish chat is not configured");
        }
        byte[] photo = post.photo();
        return photo != null
                ? bot.sendPhoto(properties.publishChat(), photo, captions.caption(post, PHOTO_LIMIT))
                : bot.sendMessage(properties.publishChat(), captions.caption(post, TEXT_LIMIT));
    }
}
