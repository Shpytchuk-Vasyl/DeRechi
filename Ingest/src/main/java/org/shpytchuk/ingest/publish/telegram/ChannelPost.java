package org.shpytchuk.ingest.publish.telegram;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.event.ItemCreatedEvent;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.publish.Publication;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

public record ChannelPost(
        ItemKind kind,
        String title,
        @Nullable String description,
        String placeName,
        LocalDate date,
        @Nullable Money compensation,
        byte @Nullable [] photo,
        @Nullable String itemUrl,
        @Nullable String city,
        @Nullable String category) {

    public ChannelPost(ItemKind kind, String title, @Nullable String description, String placeName, LocalDate date,
                       @Nullable Money compensation, byte @Nullable [] photo, @Nullable String itemUrl) {
        this(kind, title, description, placeName, date, compensation, photo, itemUrl, null, null);
    }

    static ChannelPost of(Publication publication) {
        Notice notice = publication.notice();
        return new ChannelPost(notice.kind(), notice.title(), notice.description(), notice.place().name(),
                notice.date(), notice.compensation(), notice.photo(), publication.siteItemUrl(), city(publication),
                publication.result().extraction().category());
    }

    static ChannelPost of(ItemKind kind, ItemCreatedEvent event, byte @Nullable [] photo, String itemUrl) {
        Money compensation = event.compensation() == null ? null : new Money(event.compensation(), event.currency());
        return new ChannelPost(kind, event.title(), event.description(), event.placeName(), event.date(), compensation,
                photo, itemUrl, null, event.categoryKey());
    }

    private static @Nullable String city(Publication publication) {
        String named = publication.result().extraction().city();
        return StringUtils.hasText(named) ? named : publication.post().settings().defaultCity();
    }
}
