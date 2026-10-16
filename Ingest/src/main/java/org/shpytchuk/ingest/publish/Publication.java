package org.shpytchuk.ingest.publish;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.source.IncomingPost;

public record Publication(IncomingPost post, Notice notice, ExtractionResult result, @Nullable SiteItem siteItem) {

    public @Nullable String siteItemUrl() {
        return siteItem == null ? null : siteItem.url();
    }
}
