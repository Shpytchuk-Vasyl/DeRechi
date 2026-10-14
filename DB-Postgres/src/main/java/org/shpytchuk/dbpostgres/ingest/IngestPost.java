package org.shpytchuk.dbpostgres.ingest;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "ingest_post",
        uniqueConstraints = @UniqueConstraint(name = "ingest_post_source_channel_external_uq",
                columnNames = {"source", "channel", "external_id"}),
        indexes = {
                @Index(name = "ingest_post_phone_hash_idx", columnList = "phone_hash, created_at"),
                @Index(name = "ingest_post_item_idx", columnList = "item_kind, item_id")})
public class IngestPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String source;

    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String channel;

    @Size(max = 255)
    @Column(nullable = false)
    private String externalId;

    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String status;

    @Size(max = 64)
    @Column(length = 64)
    private String phoneHash;

    @Size(max = 5)
    @Column(length = 5)
    private String itemKind;

    private Long itemId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
