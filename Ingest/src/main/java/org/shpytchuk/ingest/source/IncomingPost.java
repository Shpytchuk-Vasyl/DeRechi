package org.shpytchuk.ingest.source;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record IncomingPost(
        PostKey key,
        String sourceTitle,
        @Nullable String link,
        Instant postedAt,
        String text,
        List<PhotoRef> photos,
        SourceSettings settings,
        @Nullable SourceDetails details) {
}
