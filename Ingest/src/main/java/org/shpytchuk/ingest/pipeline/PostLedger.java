package org.shpytchuk.ingest.pipeline;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.entity.IngestPost;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.repository.IngestPostRepository;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PostKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@AllArgsConstructor
@Slf4j
public class PostLedger {

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
