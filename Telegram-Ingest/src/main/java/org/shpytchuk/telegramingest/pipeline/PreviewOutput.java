package org.shpytchuk.telegramingest.pipeline;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.telegramingest.channel.Preview;
import org.shpytchuk.telegramingest.channel.ReviewChat;
import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.llm.ExtractionResult;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.telegram.IncomingPost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** DRY_RUN: for tests */
@Component
@ConditionalOnProperty(prefix = "derechi.ingest", name = "mode", havingValue = "DRY_RUN", matchIfMissing = true)
@AllArgsConstructor
@Slf4j
public class PreviewOutput implements NoticeOutput {

    private final ReviewChat reviewChat;
    private final PostLedger ledger;
    private final IngestMetrics metrics;

    @Override
    public void accept(IncomingPost post, Notice notice, ExtractionResult result, List<byte[]> photos) {
        ledger.record(post, PostStatus.PREVIEWED, notice.phoneHash(), notice.kind());
        reviewChat.send(new Preview(post.chatTitle(), post.link(), Outcome.PREVIEWED.tag(),
                notice.channelPost(post, null), result.json(), photos));
        metrics.outcome(Outcome.PREVIEWED);
        log.info("Post {}/{} previewed as a {} notice", post.chatId(), post.messageId(), notice.kind().path());
    }

    @Override
    public void skipped(IncomingPost post, Outcome outcome, ExtractionResult result, List<byte[]> photos) {
        reviewChat.send(new Preview(post.chatTitle(), post.link(), outcome.tag(), null, result.json(), photos));
    }
}
