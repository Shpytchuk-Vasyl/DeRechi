package org.shpytchuk.dbpostgres.ingest;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "telegram_post",
        uniqueConstraints = @UniqueConstraint(name = "telegram_post_chat_message_uq", columnNames = {"chat_id", "message_id"}),
        indexes = @Index(name = "telegram_post_phone_hash_idx", columnList = "phone_hash, created_at"))
public class TelegramPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long chatId;

    @Column(nullable = false)
    private long messageId;

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
