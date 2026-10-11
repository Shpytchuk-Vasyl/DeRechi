package org.shpytchuk.telegramingest.pipeline;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.telegramingest.channel.Preview;
import org.shpytchuk.telegramingest.channel.ReviewChat;
import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.llm.ExtractionResult;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.telegram.IncomingPost;
import org.shpytchuk.telegramingest.telegram.TelegramSource;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PreviewOutputTest {

    static final byte[] PHOTO = {2};

    ReviewChat reviewChat = mock(ReviewChat.class);
    PostLedger ledger = mock(PostLedger.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PreviewOutput output = new PreviewOutput(reviewChat, ledger,
            new IngestMetrics(registry, Clock.systemUTC(), mock(TelegramSource.class)));

    @Test
    void noticeIsRecordedAsPreviewedAndShownWithoutALink() {
        IncomingPost post = Outputs.post();
        ExtractionResult result = Outputs.result();

        output.accept(post, Outputs.notice(PHOTO), result, List.of(PHOTO));

        verify(ledger).record(eq(post), eq(PostStatus.PREVIEWED), eq(Outputs.PHONE_HASH), eq(ItemKind.FOUND));
        Preview preview = sent();
        assertThat(preview.outcome()).isEqualTo("previewed");
        assertThat(preview.sourceLink()).isEqualTo("https://t.me/zagubleno911/10");
        assertThat(preview.post().itemUrl()).isNull();
        assertThat(preview.post().title()).isEqualTo("Гаманець");
        assertThat(preview.extractionJson()).isEqualTo(result.json());
        assertThat(preview.photos()).containsExactly(PHOTO);
        assertThat(registry.counter("derechi.ingest.posts", "outcome", "previewed").count()).isEqualTo(1);
    }

    @Test
    void skipIsShownWithItsReasonAndNothingIsRecorded() {
        output.skipped(Outputs.post(), Outcome.NO_PLACE, Outputs.result(), List.of());

        Preview preview = sent();
        assertThat(preview.outcome()).isEqualTo("no_place");
        assertThat(preview.post()).isNull();
        verifyNoInteractions(ledger);
    }

    private Preview sent() {
        ArgumentCaptor<Preview> preview = ArgumentCaptor.forClass(Preview.class);
        verify(reviewChat).send(preview.capture());
        return preview.getValue();
    }
}
