package org.shpytchuk.ingest.source.telegram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.phone.PhoneExtractor;
import org.shpytchuk.ingest.source.IncomingPost;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
@Slf4j
public class AuthorPhoneResolver {

    private static final int MAX_USERNAME_LOOKUPS = 3;

    private final TelegramSource telegram;
    private final PhoneExtractor phones;

    private final Cache<String, Optional<Long>> userIds = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    public AuthorPhoneResolver(TelegramSource telegram, PhoneExtractor phones) {
        this.telegram = telegram;
        this.phones = phones;
    }

    public Optional<String> resolve(IncomingPost post) {
        if (!(post.details() instanceof TelegramDetails details)) {
            return Optional.empty();
        }
        Set<Long> tried = new HashSet<>();
        Optional<String> linked = Stream.concat(Stream.ofNullable(details.authorUserId()), details.mentionedUserIds().stream())
                .flatMap(userId -> phoneOf(userId, tried, post).stream())
                .findFirst();
        if (linked.isPresent()) {
            return linked;
        }

        int lookups = 0;
        for (String mention : details.mentionedUsernames()) {
            String username = mention.toLowerCase(Locale.ROOT);
            if (username.equalsIgnoreCase(details.chatUsername())) {
                continue;
            }
            Optional<Long> userId = userIds.getIfPresent(username);
            if (userId == null) {
                if (lookups++ == MAX_USERNAME_LOOKUPS) {
                    break;
                }
                userId = lookUp(username, post);
            }
            Optional<String> phone = userId.flatMap(id -> phoneOf(id, tried, post));
            if (phone.isPresent()) {
                return phone;
            }
        }
        return Optional.empty();
    }

    private Optional<String> phoneOf(long userId, Set<Long> tried, IncomingPost post) {
        if (!tried.add(userId)) {
            return Optional.empty();
        }
        try {
            return telegram.userPhone(userId).flatMap(phones::profilePhone);
        } catch (IngestException e) {
            log.warn("Profile phone lookup failed for post {}: {}", post.key(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Long> lookUp(String username, IncomingPost post) {
        try {
            Optional<Long> userId = telegram.userIdByUsername(username);
            userIds.put(username, userId);
            return userId;
        } catch (IngestException e) {
            log.warn("Username lookup failed for post {}: {}", post.key(), e.getMessage());
            return Optional.empty();
        }
    }
}
