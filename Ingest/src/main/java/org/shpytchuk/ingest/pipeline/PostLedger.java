package org.shpytchuk.ingest.pipeline;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.entity.IngestPost;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.repository.IngestPostRepository;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PostKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class PostLedger {

    public static final String INTERNAL = "internal";

    private final IngestPostRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean seen(IncomingPost post) {
        PostKey key = post.key();
        return repository.existsBySourceAndChannelAndExternalId(key.source(), key.channel(), key.id());
    }

    @Transactional(readOnly = true)
    public boolean duplicate(Iterable<String> phoneHashes, Duration window) {
        Instant since = clock.instant().minus(window);
        for (String hash : phoneHashes) {
            if (repository.existsByPhoneHashAndItemKindIsNotNullAndCreatedAtAfter(hash, since)) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean publishing() {
        return repository.existsByStatus(PostStatus.PUBLISHING);
    }

    @Transactional(readOnly = true)
    public boolean owns(ItemKind kind, long itemId) {
        return repository.existsByItemKindAndItemId(kind, itemId);
    }

    @Transactional
    public void posted(ItemKind kind, long itemId, long messageId) {
        IngestPost row = repository.findFirstByItemKindAndItemIdOrderByIdAsc(kind, itemId).orElseThrow();
        row.setMessageId(messageId);
        row.setUpdatedAt(clock.instant());
    }

    @Transactional
    public void postedFromSite(String origin, ItemKind kind, long itemId, long messageId) {
        IngestPost row = new IngestPost(new PostKey(origin, INTERNAL, internalId(kind, itemId)), PostStatus.PUBLISHED,
                clock.instant());
        row.setItemKind(kind);
        row.setItemId(itemId);
        row.setMessageId(messageId);
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public Optional<Long> messageId(ItemKind kind, long itemId) {
        return repository.findByItemKindAndItemIdAndMessageIdNotNull(kind, itemId).stream()
                .map(IngestPost::getMessageId)
                .findFirst();
    }

    @Transactional
    public void removedFromChannel(ItemKind kind, long itemId) {
        Instant now = clock.instant();
        repository.findByItemKindAndItemIdAndMessageIdNotNull(kind, itemId).forEach(row -> {
            row.setMessageId(null);
            row.setUpdatedAt(now);
        });
    }

    static String internalId(ItemKind kind, long itemId) {
        return kind.path() + "/" + itemId;
    }

    @Transactional
    public long record(IncomingPost post, PostStatus status, @Nullable String phoneHash, @Nullable ItemKind kind) {
        IngestPost row = new IngestPost(post.key(), status, clock.instant());
        row.setPhoneHash(phoneHash);
        row.setItemKind(kind);
        return repository.save(row).getId();
    }

    @Transactional
    public void published(long rowId, long itemId) {
        IngestPost row = repository.findById(rowId).orElseThrow();
        row.setStatus(PostStatus.PUBLISHED);
        row.setItemId(itemId);
        row.setUpdatedAt(clock.instant());
    }

    @Transactional
    public void move(long rowId, PostStatus status) {
        IngestPost row = repository.findById(rowId).orElseThrow();
        row.setStatus(status);
        row.setUpdatedAt(clock.instant());
    }

    @Transactional
    public int parkInterrupted() {
        int parked = repository.moveAll(PostStatus.PUBLISHING, PostStatus.NEEDS_CHECK, clock.instant());
        if (parked > 0) {
            log.warn("{} post(s) were interrupted while publishing and need a manual check (status NEEDS_CHECK)", parked);
        }
        return parked;
    }
}
