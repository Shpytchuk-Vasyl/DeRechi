package org.shpytchuk.ingest.source.telegram;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.IncomingPost;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
public class AuthorProfileResolver {

    private static final String PROFILE_URL = "https://t.me/";

    private final TelegramSource telegram;

    public Optional<AuthorProfile> resolve(IncomingPost post) {
        if (!(post.details() instanceof TelegramDetails details) || details.authorUserId() == null) {
            return Optional.empty();
        }
        try {
            return telegram.username(details.authorUserId()).map(AuthorProfileResolver::profile);
        } catch (IngestException e) {
            log.warn("Profile username lookup failed for post {}: {}", post.key(), e.getMessage());
            return Optional.empty();
        }
    }

    private static AuthorProfile profile(String username) {
        return new AuthorProfile(SocialMedia.TELEGRAM, "@" + username, PROFILE_URL + username);
    }
}
