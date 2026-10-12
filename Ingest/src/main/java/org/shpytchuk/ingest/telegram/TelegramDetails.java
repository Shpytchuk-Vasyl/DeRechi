package org.shpytchuk.ingest.telegram;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.source.SourceDetails;

import java.util.List;

public record TelegramDetails(
        long chatId,
        long messageId,
        @Nullable String chatUsername,
        @Nullable Long authorUserId,
        List<String> mentionedUsernames,
        List<Long> mentionedUserIds) implements SourceDetails {
}
