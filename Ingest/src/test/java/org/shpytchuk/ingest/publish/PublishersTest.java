package org.shpytchuk.ingest.publish;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.pipeline.IngestMetrics;
import org.shpytchuk.ingest.pipeline.PostLedger;
import org.shpytchuk.ingest.publish.site.SitePublisher;
import org.shpytchuk.ingest.source.telegram.TelegramSource;
import org.shpytchuk.ingest.support.Outputs;

import java.time.Clock;
import java.time.Duration;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublishersTest {

    static final byte[] PHOTO = {2};
    static final SiteItem ITEM = new SiteItem(ItemKind.FOUND, 42L, "https://derechi.example/uk/found/42");

    SitePublisher site = mock(SitePublisher.class);
    Publisher channel = publisher("channel", true);
    Publisher other = publisher("other", true);
    PostLedger ledger = mock(PostLedger.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void withoutTheSiteANoticeIsPreviewed() {
        publishers().publish(Outputs.post(), Outputs.notice(PHOTO), Outputs.result());

        verifyNoInteractions(site);
        verify(ledger).record(any(), eq(PostStatus.PREVIEWED), eq(Outputs.PHONE_HASH), eq(ItemKind.FOUND));
        verify(channel, never()).publish(any());
        assertThat(count("derechi.ingest.posts", "outcome", "previewed")).isEqualTo(1);
    }

    @Test
    void theSiteGoesFirstAndEveryoneElseLinksToItsItem() {
        when(site.publish(any(), any())).thenReturn(Optional.of(ITEM));

        publishers("channel", "site", "other").publish(Outputs.post(), Outputs.notice(PHOTO), Outputs.result());

        verify(ledger, never()).record(any(), eq(PostStatus.PREVIEWED), any(), any());
        assertThat(published(channel).siteItemUrl()).isEqualTo(ITEM.url());
        assertThat(published(other).siteItem()).isEqualTo(ITEM);
        assertThat(count("derechi.ingest.published", "publisher", "channel")).isEqualTo(1);
    }

    @Test
    void nothingIsAnnouncedWhenTheSiteFails() {
        when(site.publish(any(), any())).thenReturn(Optional.empty());

        publishers("site", "channel", "other").publish(Outputs.post(), Outputs.notice(PHOTO), Outputs.result());

        verify(channel, never()).publish(any());
        verify(other, never()).publish(any());
    }

    @Test
    void oneFailedPublisherDoesNotStopTheOthers() {
        when(site.publish(any(), any())).thenReturn(Optional.of(ITEM));
        doThrow(new IngestException("Bot API sendPhoto failed: HTTP 400")).when(channel).publish(any());

        publishers("site", "channel", "other").publish(Outputs.post(), Outputs.notice(PHOTO), Outputs.result());

        verify(other).publish(any());
        assertThat(registry.counter("derechi.ingest.published", "publisher", "channel", "outcome", "failed").count())
                .isEqualTo(1);
    }

    @Test
    void aPublisherThatLinksToTheSiteNeedsTheSite() {
        assertThatThrownBy(() -> publishers("channel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add site");
    }

    @Test
    void anUnknownPublisherFailsTheStart() {
        assertThatThrownBy(() -> publishers("site", "viber"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown publisher viber");
    }

    private Publishers publishers(String... names) {
        IngestProperties properties = new IngestProperties(List.of(names), "key", Duration.ofDays(14), 3,
                Period.ofDays(30), "https://derechi.example", "uk", 0.6, null);
        return new Publishers(properties, site, List.of(channel, other), ledger,
                new IngestMetrics(registry, Clock.systemUTC(), mock(TelegramSource.class)));
    }

    private static Publisher publisher(String name, boolean needsSiteItem) {
        Publisher publisher = mock(Publisher.class);
        when(publisher.name()).thenReturn(name);
        when(publisher.needsSiteItem()).thenReturn(needsSiteItem);
        return publisher;
    }

    private static Publication published(Publisher publisher) {
        ArgumentCaptor<Publication> publication = ArgumentCaptor.forClass(Publication.class);
        verify(publisher).publish(publication.capture());
        return publication.getValue();
    }

    private double count(String meter, String tag, String value) {
        var counter = registry.find(meter).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }
}
