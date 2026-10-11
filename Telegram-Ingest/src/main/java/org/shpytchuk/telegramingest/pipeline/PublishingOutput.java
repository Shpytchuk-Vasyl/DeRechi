package org.shpytchuk.telegramingest.pipeline;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.api.DeRechiApi;
import org.shpytchuk.telegramingest.channel.ChannelPoster;
import org.shpytchuk.telegramingest.config.IngestProperties;
import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.RejectedItemException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.llm.ExtractionResult;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.storage.ImageUploader;
import org.shpytchuk.telegramingest.telegram.IncomingPost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalLong;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@ConditionalOnProperty(prefix = "derechi.ingest", name = "mode", havingValue = "AUTO")
@AllArgsConstructor
@Slf4j
public class PublishingOutput implements NoticeOutput {

    private final IngestProperties properties;
    private final ImageUploader uploader;
    private final DeRechiApi api;
    private final ChannelPoster channel;
    private final PostLedger ledger;
    private final IngestMetrics metrics;

    @Override
    public void accept(IncomingPost post, Notice notice, ExtractionResult result, List<byte[]> photos) {
        String imageKey = notice.photo() == null ? null : uploader.upload(notice.photo());
        long rowId = ledger.record(post, PostStatus.PUBLISHING, notice.phoneHash(), notice.kind());

        OptionalLong itemId = createItem(post, notice, imageKey, rowId);
        if (itemId.isEmpty()) {
            metrics.outcome(Outcome.FAILED);
            return;
        }
        ledger.published(rowId, itemId.getAsLong());
        postToChannel(post, notice, rowId, itemId.getAsLong());
        metrics.outcome(Outcome.PUBLISHED);
    }

    private OptionalLong createItem(IncomingPost post, Notice notice, @Nullable String imageKey, long rowId) {
        try {

            long itemId = api.create(notice.newItem(imageKey));
            log.info("Post {}/{} published as {} item {}", post.chatId(), post.messageId(), notice.kind().path(), itemId);
            return OptionalLong.of(itemId);

        } catch (RejectedItemException e) {
            ledger.move(rowId, PostStatus.FAILED);
            log.warn("Post {}/{} rejected by Client-API: {}", post.chatId(), post.messageId(), e.getMessage());
        } catch (RuntimeException e) {
            ledger.move(rowId, PostStatus.NEEDS_CHECK);
            log.error("Post {}/{} may or may not be on the site (NEEDS_CHECK): {}",
                    post.chatId(), post.messageId(), getMostSpecificCause(e).toString());
        }
        return OptionalLong.empty();
    }

    private void postToChannel(IncomingPost post, Notice notice, long rowId, long itemId) {
        String kind = notice.kind().path();
        try {

            long messageId = channel.post(notice.channelPost(post, itemUrl(kind, itemId)));
            ledger.move(rowId, PostStatus.DONE);
            log.info("{} item {} posted to the channel as message {}", kind, itemId, messageId);

        } catch (StageUnavailableException e) {
            log.warn("{} item {} stays off the channel: {}", kind, itemId, e.getMessage());
        } catch (IngestException e) {
            ledger.move(rowId, PostStatus.FAILED);
            log.warn("{} item {} is on the site but not in the channel: {}", kind, itemId, e.getMessage());
        }
    }

    private String itemUrl(String kind, long itemId) {
        return "%s/%s/%s/%d".formatted(properties.siteUrl(), properties.siteLocale(), kind, itemId);
    }
}
