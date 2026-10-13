package org.shpytchuk.ingest.source.facebook;

import org.jspecify.annotations.Nullable;

import java.util.List;

record FeedPage(List<FacebookStory> stories, @Nullable String nextCursor) {
}
