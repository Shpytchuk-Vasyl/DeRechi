package org.shpytchuk.telegramingest.telegram;

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
