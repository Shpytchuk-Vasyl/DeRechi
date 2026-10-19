package org.shpytchuk.ingest.publish.telegram;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.MessageNotEditableException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.pipeline.PostLedger;
import org.shpytchuk.ingest.publish.Publication;
import org.shpytchuk.ingest.publish.Publisher;
import org.shpytchuk.ingest.publish.SiteItem;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

import static org.shpytchuk.ingest.publish.telegram.CaptionBuilder.PHOTO_LIMIT;
import static org.shpytchuk.ingest.publish.telegram.CaptionBuilder.TEXT_LIMIT;

@Service
@AllArgsConstructor
@Slf4j
public class ChannelPoster implements Publisher {

    public static final String NAME = "channel";

    private final BotApiClient bot;
    private final CaptionBuilder captions;
    private final BotProperties properties;
    private final PostLedger ledger;
    private final StoredPhotos photos;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean needsSiteItem() {
        return true;
    }

    @Override
    public void publish(Publication publication) {
        SiteItem item = Objects.requireNonNull(publication.siteItem());
        long messageId = post(ChannelPost.of(publication, photo(publication.notice())));
        ledger.posted(item.kind(), item.id(), messageId);
        log.info("Post {} posted to the channel as message {}", publication.post().key(), messageId);
    }

    public void postFromSite(String origin, ItemKind kind, long itemId, ChannelPost post) {
        long messageId = post(post);
        ledger.postedFromSite(origin, kind, itemId, messageId);
        log.info("{} {} item {} posted to the channel as message {}", origin, kind.path(), itemId, messageId);
    }

    private byte @Nullable [] photo(Notice notice) {
        return notice.photo() != null ? notice.photo() : photos.load(notice.placeholderPhoto());
    }


    public boolean edit(ItemKind kind, long itemId, ChannelPost post) {
        Optional<Long> messageId = ledger.messageId(kind, itemId);
        if (messageId.isEmpty()) {
            return false;
        }
        try {
            edit(messageId.get(), post);
        } catch (MessageNotEditableException e) {
            log.info("Channel message {} of {} item {} takes no edit ({}), posting it anew", messageId.get(), kind.path(),
                    itemId, e.getMessage());
            deleteQuietly(messageId.get());
            ledger.posted(kind, itemId, post(post));
        }
        return true;
    }

    private void edit(long messageId, ChannelPost post) {
        if (!properties.canPublish()) {
            throw new StageUnavailableException("Bot token or publish chat is not configured");
        }
        byte[] photo = post.photo();
        if (photo != null) {
            bot.editMessageMedia(properties.publishChat(), messageId, photo, captions.caption(post, PHOTO_LIMIT));
        } else {
            bot.editMessageText(properties.publishChat(), messageId, captions.caption(post, TEXT_LIMIT));
        }
    }

    private void deleteQuietly(long messageId) {
        try {
            bot.deleteMessage(properties.publishChat(), messageId);
        } catch (IngestException e) {
            log.warn("Channel message {} stays next to its replacement: {}", messageId, e.getMessage());
        }
    }

    public boolean delete(ItemKind kind, long itemId) {
        Optional<Long> messageId = ledger.messageId(kind, itemId);
        if (messageId.isEmpty()) {
            return false;
        }
        bot.deleteMessage(properties.publishChat(), messageId.get());
        ledger.removedFromChannel(kind, itemId);
        return true;
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
