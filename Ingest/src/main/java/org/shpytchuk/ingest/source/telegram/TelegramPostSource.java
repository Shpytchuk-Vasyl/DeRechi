package org.shpytchuk.ingest.source.telegram;

import lombok.AllArgsConstructor;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostSource;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

@Component
@AllArgsConstructor
public class TelegramPostSource implements PostSource {

    public static final String NAME = "telegram";

    private final TelegramSource telegram;
    private final AuthorPhoneResolver authorPhones;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void start(Consumer<IncomingPost> sink) {
        telegram.start(sink);
    }

    @Override
    public byte[] download(PhotoRef photo) {
        return telegram.download(photo);
    }

    @Override
    public Optional<String> authorPhone(IncomingPost post) {
        return authorPhones.resolve(post);
    }
}
