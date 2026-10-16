package org.shpytchuk.ingest.source.facebook;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.source.PhotoRef;

import java.time.Instant;
import java.util.List;

record FacebookStory(String postId, Instant createdAt, String text, List<PhotoRef> photos,
                     @Nullable FacebookActor actor) {
}
