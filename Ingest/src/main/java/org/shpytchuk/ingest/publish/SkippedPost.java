package org.shpytchuk.ingest.publish;

import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.source.IncomingPost;

import java.util.List;

public record SkippedPost(IncomingPost post, Outcome outcome, ExtractionResult result, List<byte[]> photos) {
}
