package org.shpytchuk.ingest.source.telegram;

import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface TelegramSource {

    void start(Consumer<IncomingPost> sink);

    List<IncomingPost> recentPosts(Duration maxAge, int maxMessages);

    Optional<String> userPhone(long userId);

    Optional<Long> userIdByUsername(String username);

    byte[] download(PhotoRef photo);

    boolean authorized();
}
