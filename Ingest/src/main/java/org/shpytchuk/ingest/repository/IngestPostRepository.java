package org.shpytchuk.ingest.repository;

import org.shpytchuk.ingest.entity.IngestPost;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.model.ItemKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface IngestPostRepository extends JpaRepository<IngestPost, Long> {

    boolean existsBySourceAndChannelAndExternalId(String source, String channel, String externalId);

    Optional<IngestPost> findBySourceAndChannelAndExternalId(String source, String channel, String externalId);

    boolean existsByPhoneHashAndItemKindIsNotNullAndCreatedAtAfter(String phoneHash, Instant since);

    boolean existsByStatus(PostStatus status);

    boolean existsByItemKindAndItemId(ItemKind kind, long itemId);

    @Modifying
    @Query("update IngestPost p set p.status = :to, p.updatedAt = :now where p.status = :from")
    int moveAll(PostStatus from, PostStatus to, Instant now);
}
