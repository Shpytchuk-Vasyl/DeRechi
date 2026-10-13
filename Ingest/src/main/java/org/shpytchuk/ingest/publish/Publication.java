package org.shpytchuk.ingest.publish;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.source.IncomingPost;

import java.util.List;

public record Publication(IncomingPost post, Notice notice, ExtractionResult result, List<byte[]> photos,
                          @Nullable SiteItem siteItem) {

    public @Nullable String siteItemUrl() {
        return siteItem == null ? null : siteItem.url();
    }
}
