package org.shpytchuk.telegramingest.pipeline;

import org.shpytchuk.telegramingest.llm.ExtractionResult;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.telegram.IncomingPost;

import java.util.List;

public interface NoticeOutput {

    void accept(IncomingPost post, Notice notice, ExtractionResult result, List<byte[]> photos);

    default void skipped(IncomingPost post, Outcome outcome, ExtractionResult result, List<byte[]> photos) {
    }
}
