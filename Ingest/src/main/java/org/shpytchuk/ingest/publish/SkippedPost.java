package org.shpytchuk.ingest.publish;

import org.shpytchuk.ingest.api.ai.llm.ExtractionResult;
import org.shpytchuk.ingest.api.ai.model.Outcome;
import org.shpytchuk.ingest.source.IncomingPost;

import java.util.List;

public record SkippedPost(IncomingPost post, Outcome outcome, ExtractionResult result, List<byte[]> photos) {
}
