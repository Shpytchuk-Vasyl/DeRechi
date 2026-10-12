package org.shpytchuk.ingest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.source.PostKey;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "ingest_post_source_channel_external_uq",
        columnNames = {"source", "channel", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class IngestPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, length = 100)
    private String channel;

    @Column(nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(length = 64)
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private ItemKind itemKind;

    private Long itemId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public IngestPost(PostKey key, PostStatus status, Instant now) {
        this.source = key.source();
        this.channel = key.channel();
        this.externalId = key.id();
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
