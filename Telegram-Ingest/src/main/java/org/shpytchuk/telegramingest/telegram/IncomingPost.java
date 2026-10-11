package org.shpytchuk.telegramingest.telegram;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record IncomingPost(
        long chatId,
        long messageId,
        String chatTitle,
        @Nullable String chatUsername,
        Instant postedAt,
        String text,
        List<PhotoRef> photos,
        @Nullable Long authorUserId,
        List<String> mentionedUsernames,
        List<Long> mentionedUserIds,
        ChatSettings chat) {

    public @Nullable String link() {
        // TDLib message ids are server ids shifted by 20 bits
        return chatUsername == null ? null : "https://t.me/%s/%d".formatted(chatUsername, messageId >> 20);
    }
}
