package org.shpytchuk.ingest.source;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PostSources {

    private final Map<String, PostSource> byName;

    public PostSources(List<PostSource> sources) {
        this.byName = sources.stream().collect(Collectors.toUnmodifiableMap(PostSource::name, Function.identity()));
    }

    public void start(Consumer<IncomingPost> sink) {
        byName.values().forEach(source -> source.start(sink));
    }

    public PostSource of(IncomingPost post) {
        PostSource source = byName.get(post.key().source());
        if (source == null) {
            throw new IllegalStateException("No source " + post.key().source() + " for post " + post.key());
        }
        return source;
    }
}
