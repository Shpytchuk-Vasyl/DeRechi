package org.shpytchuk.ingest.publish.site;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.client.DeRechiApi;
import org.shpytchuk.ingest.client.NewItem;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.RejectedItemException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.pipeline.IngestMetrics;
import org.shpytchuk.ingest.pipeline.PostLedger;
import org.shpytchuk.ingest.publish.SiteItem;
import org.shpytchuk.ingest.source.telegram.TelegramSource;
import org.shpytchuk.ingest.support.Outputs;

import java.time.Clock;
import java.time.Duration;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SitePublisherTest {

    static final byte[] PHOTO = {2};

    ImageUploader uploader = mock(ImageUploader.class);
    DeRechiApi api = mock(DeRechiApi.class);
    PostLedger ledger = mock(PostLedger.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SitePublisher site = new SitePublisher(
            new IngestProperties(List.of("site"), "key", Duration.ofDays(14), 3, Period.ofDays(30),
                    "https://derechi.example", "uk", 0.6),
            uploader, api, ledger, new IngestMetrics(registry, Clock.systemUTC(), mock(TelegramSource.class)));

    @BeforeEach
    void defaults() {
        when(ledger.record(any(), any(), any(), any())).thenReturn(7L);
        when(uploader.upload(any())).thenReturn("items/2026/10/x.jpg");
        when(api.create(any())).thenReturn(42L);
    }

    @Test
    void createsTheItemAndLinksToIt() {
        Optional<SiteItem> item = site.publish(Outputs.post(), Outputs.notice(PHOTO));

        verify(uploader).upload(PHOTO);
        verify(ledger).record(any(), eq(PostStatus.PUBLISHING), eq(Outputs.PHONE_HASH), eq(ItemKind.FOUND));
        ArgumentCaptor<NewItem> created = ArgumentCaptor.forClass(NewItem.class);
        verify(api).create(created.capture());
        assertThat(created.getValue().imageKey()).isEqualTo("items/2026/10/x.jpg");
        assertThat(created.getValue().phone()).isEqualTo(Outputs.PHONE);
        verify(ledger).published(7L, 42L);
        assertThat(item).contains(new SiteItem(ItemKind.FOUND, 42L, "https://derechi.example/uk/found/42"));
        assertThat(count(Outcome.PUBLISHED)).isEqualTo(1);
    }

    @Test
    void noticeWithoutPhotoUploadsNothing() {
        site.publish(Outputs.post(), Outputs.notice(null));

        verifyNoInteractions(uploader);
        ArgumentCaptor<NewItem> created = ArgumentCaptor.forClass(NewItem.class);
        verify(api).create(created.capture());
        assertThat(created.getValue().imageKey()).isNull();
    }

    @Test
    void rejectedNoticeFails() {
        when(api.create(any())).thenThrow(new RejectedItemException("title: too long"));

        assertThat(site.publish(Outputs.post(), Outputs.notice(PHOTO))).isEmpty();

        verify(ledger).move(7L, PostStatus.FAILED);
        assertThat(count(Outcome.FAILED)).isEqualTo(1);
    }

    @Test
    void unclearCreateIsParkedForACheck() {
        when(api.create(any())).thenThrow(new IngestException("Client-API timed out"));

        assertThat(site.publish(Outputs.post(), Outputs.notice(PHOTO))).isEmpty();

        verify(ledger).move(7L, PostStatus.NEEDS_CHECK);
        verify(ledger, never()).published(anyLong(), anyLong());
    }

    private double count(Outcome outcome) {
        return registry.counter("derechi.ingest.posts", "outcome", outcome.tag()).count();
    }
}
