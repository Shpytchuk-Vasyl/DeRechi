package org.shpytchuk.ingest.telegram;

import it.tdlight.jni.TdApi;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.joining;

final class MessageMapper {

    private static final String PARAGRAPH = "\n\n";

    private MessageMapper() {
    }

    static Optional<MessagePart> part(TdApi.Message message, FolderChat chat) {
        return map(message, chat).map(post -> new MessagePart(post, sender(message), message.mediaAlbumId));
    }

    static Optional<IncomingPost> map(TdApi.Message message, FolderChat chat) {
        if (message.isOutgoing) {
            return Optional.empty();
        }
        TdApi.FormattedText text;
        List<PhotoRef> photos;
        switch (message.content) {
            case TdApi.MessageText content -> {
                text = content.text;
                photos = List.of();
            }
            case TdApi.MessagePhoto content -> {
                text = content.caption;
                photos = largest(content.photo).stream().toList();
            }
            default -> {
                return Optional.empty();
            }
        }
        if (text.text.isBlank() && photos.isEmpty()) {
            return Optional.empty();
        }

        List<String> usernames = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        for (TdApi.TextEntity entity : text.entities) {
            switch (entity.type) {
                // the entity covers "@username"; offsets are UTF-16 units, like Java string indices
                case TdApi.TextEntityTypeMention _ ->
                        usernames.add(text.text.substring(entity.offset + 1, entity.offset + entity.length));
                case TdApi.TextEntityTypeMentionName name -> userIds.add(name.userId);
                default -> {
                }
            }
        }
        TelegramDetails details = new TelegramDetails(message.chatId, message.id, chat.username(), author(message),
                distinctIgnoringCase(usernames), userIds.stream().distinct().toList());
        return Optional.of(post(chat.title(), Instant.ofEpochSecond(message.date), text.text, photos, chat.settings(),
                details));
    }

    static IncomingPost merge(List<IncomingPost> parts) {
        List<IncomingPost> sorted = parts.stream().sorted(Comparator.comparingLong(MessageMapper::messageId)).toList();
        IncomingPost first = sorted.getFirst();
        List<TelegramDetails> details = sorted.stream().map(MessageMapper::details).toList();
        TelegramDetails merged = new TelegramDetails(details.getFirst().chatId(), details.getFirst().messageId(),
                details.getFirst().chatUsername(),
                details.stream().map(TelegramDetails::authorUserId).filter(Objects::nonNull).findFirst().orElse(null),
                distinctIgnoringCase(details.stream().flatMap(part -> part.mentionedUsernames().stream()).toList()),
                details.stream().flatMap(part -> part.mentionedUserIds().stream()).distinct().toList());
        return post(first.sourceTitle(),
                sorted.stream().map(IncomingPost::postedAt).min(Comparator.naturalOrder()).orElseThrow(),
                sorted.stream().map(IncomingPost::text).filter(text -> !text.isBlank()).distinct().collect(joining(PARAGRAPH)),
                sorted.stream().flatMap(part -> part.photos().stream()).toList(),
                first.settings(), merged);
    }

    static long messageId(IncomingPost post) {
        return details(post).messageId();
    }

    static long chatId(IncomingPost post) {
        return details(post).chatId();
    }

    private static TelegramDetails details(IncomingPost post) {
        if (post.details() instanceof TelegramDetails details) {
            return details;
        }
        throw new IllegalArgumentException("Post " + post.key() + " is not from Telegram");
    }

    private static IncomingPost post(String title, Instant postedAt, String text, List<PhotoRef> photos,
                                     SourceSettings settings, TelegramDetails details) {
        PostKey key = new PostKey(TelegramPostSource.NAME, Long.toString(details.chatId()),
                Long.toString(details.messageId()));
        return new IncomingPost(key, title, link(details), postedAt, text, photos, settings, details);
    }

    private static @Nullable String link(TelegramDetails details) {
        // TDLib message ids are server ids shifted by 20 bits
        return details.chatUsername() == null ? null
                : "https://t.me/%s/%d".formatted(details.chatUsername(), details.messageId() >> 20);
    }

    static List<IncomingPost> mapAll(List<TdApi.Message> messages, FolderChat chat, Duration wait) {
        return PostGroup.assemble(messages.stream().flatMap(message -> part(message, chat).stream()).toList(), wait);
    }

    private static Optional<PhotoRef> largest(TdApi.Photo photo) {
        return Arrays.stream(photo.sizes)
                .max(Comparator.comparingLong(size -> (long) size.width * size.height))
                .map(size -> new PhotoRef(Integer.toString(size.photo.id), size.width, size.height));
    }

    private static @Nullable Long author(TdApi.Message message) {
        if (message.forwardInfo != null && message.forwardInfo.origin instanceof TdApi.MessageOriginUser origin) {
            return origin.senderUserId;
        }
        return message.senderId instanceof TdApi.MessageSenderUser sender ? sender.userId : null;
    }

    private static String sender(TdApi.Message message) {
        TdApi.MessageOrigin origin = message.forwardInfo == null ? null : message.forwardInfo.origin;
        return switch (origin) {
            case TdApi.MessageOriginUser user -> "user " + user.senderUserId;
            case TdApi.MessageOriginHiddenUser hidden -> "hidden " + hidden.senderName;
            case TdApi.MessageOriginChat chat -> "chat " + chat.senderChatId;
            case TdApi.MessageOriginChannel channel -> "chat " + channel.chatId;
            case null, default -> switch (message.senderId) {
                case TdApi.MessageSenderUser user -> "user " + user.userId;
                case TdApi.MessageSenderChat chat -> "chat " + chat.chatId;
                case null, default -> "unknown";
            };
        };
    }

    private static List<String> distinctIgnoringCase(List<String> usernames) {
        Set<String> seen = new HashSet<>();
        return usernames.stream().filter(username -> seen.add(username.toLowerCase(Locale.ROOT))).toList();
    }
}
