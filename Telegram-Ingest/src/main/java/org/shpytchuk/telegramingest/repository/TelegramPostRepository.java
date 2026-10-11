package org.shpytchuk.telegramingest.repository;

import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.entity.TelegramPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface TelegramPostRepository extends JpaRepository<TelegramPost, Long> {

    boolean existsByChatIdAndMessageId(long chatId, long messageId);

    Optional<TelegramPost> findByChatIdAndMessageId(long chatId, long messageId);

    boolean existsByPhoneHashAndItemKindIsNotNullAndCreatedAtAfter(String phoneHash, Instant since);

    @Modifying
    @Query("update TelegramPost p set p.status = :to, p.updatedAt = :now where p.status = :from")
    int moveAll(PostStatus from, PostStatus to, Instant now);
}
