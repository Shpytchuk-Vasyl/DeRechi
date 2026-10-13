package org.shpytchuk.ingest.publish.telegram;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.BotProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.publish.Publication;
import org.shpytchuk.ingest.publish.Publisher;
import org.springframework.stereotype.Service;

import static org.shpytchuk.ingest.publish.telegram.CaptionBuilder.PHOTO_LIMIT;
import static org.shpytchuk.ingest.publish.telegram.CaptionBuilder.TEXT_LIMIT;

@Service
@AllArgsConstructor
@Slf4j
public class ChannelPoster implements Publisher {

    private final BotApiClient bot;
    private final CaptionBuilder captions;
    private final BotProperties properties;

    @Override
    public String name() {
        return "channel";
    }

    @Override
    public boolean needsSiteItem() {
        return true;
    }

    @Override
    public void publish(Publication publication) {
        long messageId = post(ChannelPost.of(publication));
        log.info("Post {} posted to the channel as message {}", publication.post().key(), messageId);
    }

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
