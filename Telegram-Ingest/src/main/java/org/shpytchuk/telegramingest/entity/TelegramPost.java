package org.shpytchuk.telegramingest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.telegramingest.model.ItemKind;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "telegram_post_chat_message_uq", columnNames = {"chat_id", "message_id"}))
@Getter
@Setter
@NoArgsConstructor
public class TelegramPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long chatId;

    @Column(nullable = false)
    private long messageId;

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

    public TelegramPost(long chatId, long messageId, PostStatus status, Instant now) {
        this.chatId = chatId;
        this.messageId = messageId;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
