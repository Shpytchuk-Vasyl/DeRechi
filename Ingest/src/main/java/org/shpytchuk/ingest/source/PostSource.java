package org.shpytchuk.ingest.source;

import java.util.Optional;
import java.util.function.Consumer;

public interface PostSource {

    String name();

    void start(Consumer<IncomingPost> sink);

    byte[] download(PhotoRef photo);

    Optional<String> authorPhone(IncomingPost post);

    Optional<AuthorProfile> authorProfile(IncomingPost post);
}
