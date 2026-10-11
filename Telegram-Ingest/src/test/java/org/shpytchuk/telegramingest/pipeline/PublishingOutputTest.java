package org.shpytchuk.telegramingest.pipeline;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.telegramingest.api.DeRechiApi;
import org.shpytchuk.telegramingest.api.NewItem;
import org.shpytchuk.telegramingest.channel.ChannelPost;
import org.shpytchuk.telegramingest.channel.ChannelPoster;
import org.shpytchuk.telegramingest.config.IngestProperties;
import org.shpytchuk.telegramingest.config.IngestProperties.IngestMode;
import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.RejectedItemException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.storage.ImageUploader;
import org.shpytchuk.telegramingest.telegram.TelegramSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublishingOutputTest {

    static final byte[] PHOTO = {2};

    ImageUploader uploader = mock(ImageUploader.class);
    DeRechiApi api = mock(DeRechiApi.class);
    ChannelPoster channel = mock(ChannelPoster.class);
    PostLedger ledger = mock(PostLedger.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PublishingOutput output = new PublishingOutput(
            new IngestProperties(IngestMode.AUTO, "key", Duration.ofDays(14), 3, Period.ofDays(30),
                    "https://derechi.example", "uk", 0.6),
            uploader, api, channel, ledger, new IngestMetrics(registry, Clock.systemUTC(), mock(TelegramSource.class)));

    @BeforeEach
    void defaults() {
        when(ledger.record(any(), any(), any(), any())).thenReturn(7L);
        when(uploader.upload(any())).thenReturn("items/2026/10/x.jpg");
        when(api.create(any())).thenReturn(42L);
    }

    @Test
    void publishesToTheSiteThenToTheChannel() {
        output.accept(Outputs.post(), Outputs.notice(PHOTO), Outputs.result(), List.of(PHOTO));

        verify(uploader).upload(PHOTO);
        verify(ledger).record(any(), eq(PostStatus.PUBLISHING), eq(Outputs.PHONE_HASH), eq(ItemKind.FOUND));
        ArgumentCaptor<NewItem> item = ArgumentCaptor.forClass(NewItem.class);
        verify(api).create(item.capture());
        assertThat(item.getValue().imageKey()).isEqualTo("items/2026/10/x.jpg");
        assertThat(item.getValue().phone()).isEqualTo(Outputs.PHONE);
        verify(ledger).published(7L, 42L);
        ArgumentCaptor<ChannelPost> post = ArgumentCaptor.forClass(ChannelPost.class);
        verify(channel).post(post.capture());
        assertThat(post.getValue().itemUrl()).isEqualTo("https://derechi.example/uk/found/42");
        verify(ledger).move(7L, PostStatus.DONE);
        assertThat(count(Outcome.PUBLISHED)).isEqualTo(1);
    }

    @Test
    void noticeWithoutPhotoUploadsNothing() {
        output.accept(Outputs.post(), Outputs.notice(null), Outputs.result(), List.of());

        verifyNoInteractions(uploader);
        ArgumentCaptor<NewItem> item = ArgumentCaptor.forClass(NewItem.class);
        verify(api).create(item.capture());
        assertThat(item.getValue().imageKey()).isNull();
    }

    @Test
    void rejectedNoticeFails() {
        when(api.create(any())).thenThrow(new RejectedItemException("title: too long"));

        output.accept(Outputs.post(), Outputs.notice(PHOTO), Outputs.result(), List.of(PHOTO));

        verify(ledger).move(7L, PostStatus.FAILED);
        verifyNoInteractions(channel);
        assertThat(count(Outcome.FAILED)).isEqualTo(1);
    }

    @Test
    void unclearCreateIsParkedForACheck() {
        when(api.create(any())).thenThrow(new IngestException("Client-API timed out"));

        output.accept(Outputs.post(), Outputs.notice(PHOTO), Outputs.result(), List.of(PHOTO));

        verify(ledger).move(7L, PostStatus.NEEDS_CHECK);
        verify(ledger, never()).published(anyLong(), anyLong());
        verifyNoInteractions(channel);
    }

    @Test
    void channelFailureKeepsTheNoticeOnTheSite() {
        when(channel.post(any())).thenThrow(new IngestException("Bot API sendPhoto failed: HTTP 400"));

        output.accept(Outputs.post(), Outputs.notice(PHOTO), Outputs.result(), List.of(PHOTO));

        verify(ledger).published(7L, 42L);
        verify(ledger).move(7L, PostStatus.FAILED);
        assertThat(count(Outcome.PUBLISHED)).isEqualTo(1);
    }

    @Test
    void unconfiguredChannelLeavesThePostPublished() {
        when(channel.post(any())).thenThrow(new StageUnavailableException("Bot token or publish chat is not configured"));

        output.accept(Outputs.post(), Outputs.notice(PHOTO), Outputs.result(), List.of(PHOTO));

        verify(ledger).published(7L, 42L);
        verify(ledger, never()).move(anyLong(), any());
    }

    private double count(Outcome outcome) {
        return registry.counter("derechi.ingest.posts", "outcome", outcome.tag()).count();
    }
}
